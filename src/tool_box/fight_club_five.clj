(ns tool-box.fight-club-five
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.set :as set])
  (:import (java.io ByteArrayInputStream File)
           (java.nio.charset StandardCharsets)))

(defprotocol ErrorHandler
  (handle-error [this category key error]))

(defprotocol ErrorHandlerFactory
  (build-error-handler [this file])
  (get-errors [this]))

(def root (io/file "resources/fc5"))

(def dest (io/file "resources/compendium.json"))

(def item-types
  {:$ "Currency"
   :A "Ammunition"
   :G "Adventuring Gear"
   :LA "Light Armor"
   :HA "Heavy Armor"
   :M "Melee Weapon"
   :MA "Magic Armor"
   :P "Potion"
   :R "Ranged Weapon"
   :RD "Rod"
   :RG "Ring"
   :S "Shield"
   :SC "Scroll"
   :ST "Staff"
   :W "Wonderous Item"
   :WD "Wand"})

(def schools-of-magic
  {:A :Abjuration
   :C :Conjuration
   :D :Divination
   :EN :Enchantment
   :EV :Evocation
   :I :Illusion
   :N :Necromancy
   :T :Transmutation})

(def sizes
  {:G :Gargantuan
   :H :Huge
   :L :Large
   :M :Medium
   :S :Small
   :T :Tiny})

(def item-properties
  {:A "Ammunition"
   :F "Finesse"
   :H "Heavy"
   :L "Light"
   :LD "Loading"
   :M "Martial"
   :R "Reach"
   :S "Special"
   :T "Thrown"
   :2H "Two-Handed"
   :V "Versatile"})

(def detail-regex
  #"^(\(((\w+[,]?\s)*\w+)\)\,\s)?((\w+\s)*\w+)( \(requires attunement(( outdoors at night)|( by a ((\w+(\,(\sor)?\s\w+)*)|(creature of (\w+) alignment))))?\))?$")

(def detail-fields
  {:detail 2
   :rarity 4
   :requires-attunement? 6
   :attunement-detail 7
   :attunement-by 10})

(defn full-trim [my-str]
  (str/trim (str/replace my-str #"\s+" " ")))

(defn regex-to-obj [my-str regex field-indexes]
  (let [results (re-find regex my-str)
        obj (reduce-kv
              #(if-let [value (get results %3)]
                 (assoc %1 %2 value)
                 %1)
              (sorted-map) field-indexes)]
    (when-not (empty? obj)
      obj)))

(defn parse-detail [{full-detail :detail :as obj}]
  (if (nil? full-detail)
    obj
    (let [{:keys [detail rarity requires-attunement? attunement-detail attunement-by]} (regex-to-obj full-detail detail-regex detail-fields)
          rarity (or rarity full-detail)
          [rarity requires-attunement? attunement-detail attunement-by] (if (str/includes? rarity " (requires attunement")
                                                                          (let [[r raq?] (str/split rarity #" \(requires attunement")
                                                                                ad (when (< 1 (count raq?)) raq?)
                                                                                ab (second (str/split ad #" by a "))]
                                                                            [r raq? ad ab])
                                                                          [rarity requires-attunement? attunement-detail attunement-by])
          requires-attunement? (when (not (nil? requires-attunement?)) true)
          attunement-detail (when attunement-detail (str/trim attunement-detail))
          attunement-by (vec (remove empty? (str/split (str/replace (or attunement-by "") #"or\s" "") #",\s")))
          attunement-by (when (not (empty? attunement-by)) attunement-by)
          attunement (or attunement-by attunement-detail requires-attunement?)
          new-props (merge
                      (if (nil? detail) {} {:detail detail})
                      (if (nil? rarity) {} {:rarity rarity})
                      (if (nil? attunement) {} {:attunement attunement}))]
      (merge (dissoc obj :detail) new-props))))

(defn un-abbreviate-enum [field enum-map]
  (fn [obj]
    (let [k (keyword (get obj field))]
      (if (nil? k)
        obj
        (assoc obj field (get enum-map k k))))))

(defn un-abbreviate-enum-set [field delim enum-map]
  (fn [obj]
    (let [property (get obj field)]
      (if (nil? property)
        obj
        (let [ks (mapv keyword (str/split property delim))]
          (assoc obj field (reduce #(assoc %1 (get enum-map %2) true) (sorted-map) ks)))))))

(defn to-boolean [field true-value]
  (fn [obj]
    (assoc obj field (= true-value (get obj field)))))

(defn node->object [{:keys [attrs content]} & {:keys [exclude]}]
  (let [attrs (or attrs {})
        content (or content [])
        content (if (vector? content) content [content])
        children (filter map? content)
        children (remove #(contains? exclude (:tag %)) children)
        text (s/join "/n" (remove empty? (filter string? content)))]
    (if (and (empty? attrs) (empty? children))
      text
      (reduce
        #(let [key (:tag %2)
               value (node->object %2)]
           (if (empty? value)
             %1
             (if (contains? %1 key)
               (let [existing (get %1 key)
                     existing (cond
                                (seq? existing) (vec existing)
                                (vector? existing) existing
                                :else [existing])]
                 (assoc %1 key (conj existing value)))
               (assoc %1 key value))))
        (merge
          (apply dissoc attrs exclude)
          (if (empty? text) {} {:_text text}))
        children))))

(defn get-prop-type [prop]
  (cond
    (vector? prop) :vector
    (seq? prop) :seq
    (set? prop) :set
    (map? prop) :map
    (string? prop) :string
    :else :primitive
    ))

(defmulti resolve-property (fn [path _ _ _] path))

(defmulti resolve-conflict (fn [path _ _ _] path))

(defmethod resolve-property :default [path key new-prop old-prop]
  (cond
    (= new-prop old-prop) new-prop

    (not= (get-prop-type new-prop) (get-prop-type old-prop))
    (throw (IllegalArgumentException. (pr-str {:key key :message "type mismatch" :path path :new-prop new-prop :old-prop old-prop})))

    (string? new-prop)
    (cond
      (str/includes? new-prop old-prop) new-prop
      (str/includes? old-prop new-prop) old-prop
      :else (throw (IllegalArgumentException. (pr-str {:key key :message "string mismatch" :path path :new-prop new-prop :old-prop old-prop}))))

    (map? new-prop) (resolve-conflict path key new-prop old-prop)

    (vector? new-prop)
    (if
      (or
        (not= (count new-prop) (count old-prop))
        (not (every? #(= (get new-prop %) (get old-prop %)) (range (count new-prop)))))
      (throw (IllegalArgumentException. (pr-str {:key key :message "cannot merge arrays - array mismatch" :path path :new-prop new-prop :old-prop old-prop})))
      new-prop)

    (set? new-prop) (into (sorted-set) (set/union new-prop old-prop))

    :else (throw (IllegalArgumentException. (pr-str {:key key :message "cannot resolve" :path path :new-prop new-prop :old-prop old-prop})))))

(defmethod resolve-conflict :default [path key new-obj old-obj]
  (let [old-keys (set (keys old-obj))
        new-keys (set (keys new-obj))
        o!n (set/difference old-keys new-keys)
        n!o (set/difference new-keys old-keys)
        o!n-obj (select-keys old-obj o!n)
        n!o-obj (select-keys new-obj n!o)
        base-obj (merge o!n-obj n!o-obj)
        sorted-obj (try
                     (into (sorted-map) base-obj)
                     (catch Exception e
                       (pp/pprint base-obj)
                       (throw e)))]
    (reduce
      #(assoc %1 %2
         (resolve-property
           (conj path %2)
           key
           (get new-obj %2)
           (get old-obj %2)))
      sorted-obj
      (set/intersection old-keys new-keys))))

(defn merge-compendium-obj [category key new-obj error-handler]
  (fn [old-obj]
    (try
      (resolve-conflict [category] key new-obj old-obj)
      (catch Throwable t
        (handle-error error-handler category key t)
        old-obj))))

(defn append-node-to-compendium [node compendium [category compendium-key-fn spec-obj-fn] error-handler]
  (let [obj (spec-obj-fn (node->object node))
        key (compendium-key-fn obj)]
    (if (contains? compendium category)
      (if (get-in compendium [category key])
        (update-in compendium [category key] (merge-compendium-obj category key obj error-handler))
        (update compendium category assoc key obj))
      (assoc compendium category (assoc (sorted-map) key obj)))))

(defn parse-split-list [obj delim old-key new-key]
  (if (empty? (get obj old-key))
    obj
    (assoc (dissoc obj old-key) new-key (str/split (get obj old-key) delim))))

(defn split-list-to-map [obj list-key pair-fn]
  (if (empty? (get obj list-key))
    obj
    (update obj list-key #(let [items (if (vector? %) % [%])]
                            (into (sorted-map) (mapv pair-fn items))))))

(defn parse-split-list-to-map [obj delim old-key new-key pair-fn]
  (split-list-to-map (parse-split-list obj delim old-key new-key) new-key pair-fn))

(def bonus-regex #"(\w+(\s\w+)*)\s([\+-]?\d+)")

(defn split-by-space [my-str]
  (let [[_ ability _ bonus] (re-find bonus-regex my-str)]
    [ability (read-string bonus)]))

(defn parse-ability [delim obj]
  (parse-split-list-to-map obj delim :ability :abilities split-by-space))

(defn parse-skills [obj]
  (split-list-to-map obj :skill split-by-space))

(defn parse-saves [obj]
  (split-list-to-map obj :save split-by-space))

(defn parse-proficiency [delim obj]
  (parse-split-list-to-map obj delim :proficiency :proficiencies #(vector % true)))

(defn parse-classes [delim obj]
  (parse-split-list-to-map obj delim :classes :classes #(vector % true)))

(defn parse-modifier [obj]
  (if (empty? (:modifier obj))
    obj
    (let [{:keys [modifier]} obj
          modifiers (if (vector? modifier) modifier [modifier])
          modifiers (map #(let [{:keys [_text category]} %] {:modifier _text :category category}) modifiers)]
      (dissoc (assoc obj :modifiers modifiers) :modifier))))

(defn parse-source [text-key obj]
  (if (empty? (text-key obj))
    obj
    (let [text (text-key obj)
          text (->> (cond
                      (seq? text) (vec text)
                      (vector? text) text
                      :else [text])
                    (reduce #(concat %1 (str/split %2 #"\n")) [])
                    (remove empty?))
          find-source #(str/starts-with? % "Source: ")
          sources (map #(str/replace % "Source: " "") (filter find-source text))
          sources (reduce #(into %1 (str/split %2 #", ")) (sorted-set) sources)
          text (full-trim (str/join " " (remove find-source text)))]
      (merge
        (if (empty? text)
          (dissoc obj text-key)
          (assoc obj text-key text))
        (if (empty? sources) {} {:sources sources} )))))

(def parse-text-and-source (partial parse-source :text))

(def parse-desc-and-source (partial parse-source :description))

(defn reduce-to-text [{:keys [text] :as obj}]
  (when-not text
    (pp/pprint obj))
  (let [text (if (coll? text) (vec text) [text])
        text (remove empty? (mapv full-trim text))
        text (if (= 1 (count text)) (first text) text)
        obj (assoc obj :text text)]
    (if (= (set (keys obj)) #{:name :text})
      (:text obj)
      (dissoc obj :name))))

(def reduce-trait (comp (partial parse-proficiency #", ") reduce-to-text))

(defn parse-multi-traits [out {:keys [name text] :as trait}]
  (if (vector? name)
    (if-not (vector? text)
      (throw (IllegalArgumentException. (pr-str trait)))
      (let [new-trait-count (min (count name) (count text))
          base-trait (dissoc
                       (merge
                         trait
                         (parse-source :text {:text (vec (remove empty? (drop new-trait-count text)))}))
                       :name :text)]
      (concat out (mapv #(assoc base-trait :name %1 :text %2) name text))))
    (conj out trait)))

(defn parse-trait-type [old-key new-key value-fn prep-fn]
  (fn [obj]
    (if (empty? (old-key obj))
      obj
      (let [traits (old-key obj)
            traits (if (vector? traits) traits [traits])
            traits (reduce parse-multi-traits [] traits)
            [obj traits] (prep-fn [obj traits])
            traits (remove #(and (nil? (:text %)) (:name %)) traits)
            _ (when (some #(and (nil? (:name %)) (:text %)) traits)
                (throw (IllegalArgumentException. (pr-str {:old-key old-key :traits traits}))))
            traits (reduce
                     #(assoc %1 (:name %2) (value-fn %2))
                     (sorted-map) traits)]
        (assoc (dissoc obj old-key) new-key traits)))))

(defn elevate-trait [trait-label trait-key new-key obj]

  (-> obj
      (assoc new-key (get-in obj [trait-label trait-key]))
      (update trait-label dissoc trait-key)))

(def feature->features (parse-trait-type :feature :features reduce-trait #(vector (first %) (mapv parse-text-and-source (second %)))))

(def trait->traits (parse-trait-type :trait :traits reduce-trait identity))

(def action->actions (parse-trait-type :action :actions reduce-trait identity))

(def reaction->reactions (parse-trait-type :reaction :reactions reduce-trait identity))

(defn reduce-legendary-traits [traits trait]
  (if (and (contains? (last traits) :name) (not (contains? trait :name)))
    (concat (drop-last traits) [(merge (last traits) trait)])
    (concat traits [trait])))

(defn prep-legendary-traits [[obj traits]]
  (let [traits (reduce reduce-legendary-traits [] traits)]
    (if-not (contains? (first traits) :name)
      [(assoc obj :legendary-action-rules (:text (first traits))) (drop 1 traits)]
      [obj traits])))

(def parse-legendary-traits (parse-trait-type :legendary :legendary reduce-trait prep-legendary-traits))

(def ability-scores [:str :dex :con :wis :int :cha])

(def stats [:passive :ac :speed :hp :cr])

(def bonuses [:skill :save :immune :conditionImmune :resist :vulnerable])

(def demographics [:type :alignment :environment :languages :size :senses])

(defn pull-fields [fields prop obj]
  (let [group (select-keys obj fields)]
    (if (empty? group)
      obj
      (apply dissoc (assoc obj prop group) fields))))

(defn parse-numbers [& {:as fields}]
  (fn [obj]
    (let [key-set (set/intersection (set (keys fields)) (set (keys obj)))]
      (reduce
        #(update %1 %2 read-string)
        obj key-set))))

(defn parse-concentration-from-duration [{:keys [duration] :as spell}]
  (if-not duration
    spell
    (let [[concentration duration] (str/split duration #", ")
          is-concentration (when duration true)
          duration (or duration concentration)]
      (merge
        (assoc spell :duration duration)
        (if is-concentration {:concentration true} {})))))

(def component-regex #"^(V?)(, )?(S)?(, )?(M \((.*)\))?\s*")

(def component-fields
  {:verbal 1 :somatic 3 :material 6})

(defn parse-components [{:keys [components] :as spell}]
  (if-not components
    spell
    (let [{:keys [verbal somatic material]} (regex-to-obj components component-regex component-fields)
          components (merge
                       (if verbal {:verbal true} {})
                       (if somatic {:somatic true} {})
                       (if material {:material material} {}))]
      (assoc spell :components components))))

(def spellcasting-regex-fields
  [{:regex #"is a[n]? (\d?\d\w\w)\-level spellcaster"
    :fields {:spellcaster-level 1}}
   {:regex #"spell\s?casting ability is (\w+)"
    :fields {:magic-ability 1}}
   {:regex #"has( the)? following (\w+) spells prepared\:"
    :fields {:spell-list-class 2}}
   {:regex #"save DC (\d?\d)\, \+(\d?\d) to hit with spell attacks"
    :fields {:spell-save-DC 1 :spell-attack-bonus 2}}])

(defn parse-monster-spellcasting [{:keys [spells slots name traits] :as monster}]
  (let [full-Spellcasting (get traits "Spellcasting")
        Spellcasting (if (coll? full-Spellcasting)
                       (first full-Spellcasting)
                       full-Spellcasting)]
    (if-not Spellcasting
      monster
      (let [results (reduce
                      (fn [out {:keys [regex fields]}]
                        (merge out (regex-to-obj Spellcasting regex fields)))
                      (sorted-map)
                      spellcasting-regex-fields)
            results (reduce
                      #(update %1 %2 read-string)
                      results
                      (set/intersection
                        #{:spell-attack-bonus :spell-save-DC}
                        (set (keys results))))
            spellcasting-obj (assoc (or results (sorted-map))
                               :slots (mapv read-string (when slots (str/split slots #",\s?")))
                               :spells (mapv str/trim (str/split (full-trim spells) #",\s?"))
                               :original-text full-Spellcasting)
            _ (when (empty? results)
                (pp/pprint (first full-Spellcasting)))
            monster (assoc monster :spellcasting spellcasting-obj)
            monster (dissoc monster :spells :slots)
            monster (update monster :traits dissoc "Spellcasting")]
        (if (empty? (:traits monster)) (dissoc monster :traits) monster)))))

(defn reduce-autolevel [{:keys [score-improvement-levels slots-by-level slots-optional features-by-level] :as out}
                        {:keys [feature level slots scoreImprovement] :as autolevel}]
  (let [level (read-string level)
        slots (or (:_text slots) slots)]
    {:score-improvement-levels (if scoreImprovement (conj score-improvement-levels level) score-improvement-levels)
     :slots-optional (or slots-optional (not (nil? (:optional slots))))
     :slots-by-level (if-not (empty? slots) (assoc slots-by-level level (mapv (comp read-string str/trim) (str/split slots #","))) slots-by-level)
     :features-by-level (if feature (assoc features-by-level level (:features (feature->features autolevel))) features-by-level)}))

(defn parse-autolevel [{:keys [autolevel] :as obj}]
  (merge
    (dissoc obj :autolevel)
    (reduce
      reduce-autolevel
      {:score-improvement-levels (sorted-set)
       :slots-optional false
       :slots-by-level (sorted-map)
       :features-by-level (sorted-map)}
      autolevel)))

(def choices
  {:item [:items :name (comp
                         parse-text-and-source
                         parse-modifier
                         parse-detail
                         (un-abbreviate-enum :type item-types)
                         (un-abbreviate-enum-set :property #"," item-properties)
                         (to-boolean :magic "YES")
                         (parse-numbers :weight :float :ac :int :strength :int))]
   :race [:races :name (comp
                         parse-desc-and-source
                         (partial elevate-trait :traits "Description" :description)
                         parse-modifier
                         (un-abbreviate-enum :size sizes)
                         (partial parse-ability #", ")
                         (partial parse-proficiency #", ")
                         trait->traits)]
   :class [:classes :name parse-autolevel]
   :feat [:feats :name (comp
                         parse-text-and-source
                         (partial parse-proficiency #", ")
                         parse-modifier)]
   :background [:background :name (comp
                                    (partial parse-proficiency #", ")
                                    elevate-trait :traits "Description" :description
                                    trait->traits)]
   :spell [:spells :name (comp
                           parse-text-and-source
                           parse-concentration-from-duration
                           parse-components
                           (partial parse-classes #", ")
                           (un-abbreviate-enum :school schools-of-magic)
                           )]
   :monster [:monsters :name (comp
                               parse-desc-and-source
                               parse-monster-spellcasting
                               trait->traits
                               action->actions
                               parse-legendary-traits
                               reaction->reactions
                               (partial pull-fields ability-scores :abilityScores)
                               (partial pull-fields stats :stats)
                               (partial pull-fields bonuses :bonuses)
                               (partial pull-fields demographics :demographics)
                               (un-abbreviate-enum :size sizes)
                               parse-skills
                               parse-saves
                               (parse-numbers
                                 :str :int
                                 :dex :int
                                 :con :int
                                 :wis :int
                                 :int :int
                                 :cha :int
                                 :passive :int
                                 :cr :ratio)
                               )]})

(defn get-choices [tag error-handler]
  (let [choice (choices tag)]
    (if (vector? choice)
      #(append-node-to-compendium %1 %2 choice error-handler)
      choice)))

(defn- append-to-compendium [zipped compendium error-handler]
  (let [content (:content (first (remove empty? zipped)))
        content (if (empty? content) [] (if (vector? content) content [content]))]
    (reduce #(let [choice (get-choices (:tag %2) error-handler)] (choice %2 %1)) compendium content)))

(defn- pre-de-code-xml-text [xml-text]
  (str/escape
    xml-text
    {"\r" ""
    \– "-"
    \— "-"
    \• "*"
    \⅕ "1/3"
    (char 8722) "-"
    (char 160) " "
    (char 65533) ""
    (char 0) ""
    (char 14) ""
    (char 20) ""
    (char 13) ""
    (char 19) ""}))

(defn- unzip-xml [xml-file]
  (zip/xml-zip
    (xml/parse
      (ByteArrayInputStream.
        (.getBytes
          (pre-de-code-xml-text
            (slurp xml-file))
          StandardCharsets/UTF_8
          )))))

(defn- parse-folder [^File file compendium error-handler-factory & {:keys [exclude]}]
  (pp/pprint (-> file .getAbsolutePath))
  (if (some #(str/includes? (-> file .getAbsolutePath) %) exclude)
    compendium
    (if (.isDirectory file)
      (reduce #(parse-folder %2 %1 error-handler-factory :exclude exclude) compendium (.listFiles file))
      (append-to-compendium (unzip-xml file) compendium (build-error-handler error-handler-factory file)))))

(defn build-error-handler-factory []
  (let [error-atom (atom [])]
    (reify ErrorHandlerFactory
      (build-error-handler [_ ^File file]
        (reify ErrorHandler
          (handle-error [_ category key error]
            (swap! error-atom conj {:category category :key key :error error :file (.getAbsolutePath file)}))))
      (get-errors [_] @error-atom))))

(defn process [source dest & {:keys [exclude]}]
  (let [error-handler-factory (build-error-handler-factory)
        json (parse-folder
               source
               (sorted-map)
               error-handler-factory
               :exclude
               (mapv
                 #(str/join File/separator (str/split % #"/"))
                 exclude))]
    (spit
      dest
      (json/generate-string
        json
        {:pretty true}))
    (let [errors (get-errors error-handler-factory)]
      (pp/pprint (count errors))
      )))

(defn process-multi [dest & sources]
  (let [error-handler-factory (build-error-handler-factory)]
    (spit
      dest
      (json/generate-string
        (reduce
          #(parse-folder %2 %1 error-handler-factory)
          (sorted-map)
          sources)
        {:pretty true}))
    (let [errors (get-errors error-handler-factory)]
      (pp/pprint (count errors))
      )))

(defn process-and-split [sources dest-folder & {:keys [exclude]}]
  (let [error-handler-factory (build-error-handler-factory)
        sources (if (vector? sources) sources [sources])
        exclusions (mapv
                     #(str/join File/separator (str/split % #"/"))
                     exclude)
        compendium (reduce
                     #(parse-folder %2 %1 error-handler-factory :exclude exclusions)
                     (sorted-map)
                     sources)]
    (doseq [[label json] compendium]
      (spit
        (io/file dest-folder (str (name label) ".json"))
        (json/generate-string
          json
          {:pretty true})))
    (let [errors (get-errors error-handler-factory)]
      (pp/pprint (count errors))
      )))
