(ns tool-box.fight-club-five
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.set :as set])
  (:import (java.io ByteArrayInputStream)
           (java.nio.charset StandardCharsets)))

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

(defn parse-detail [{full-detail :detail :as obj}]
  (if (nil? full-detail)
    obj
    (let [results (re-find detail-regex full-detail)
          {:keys [detail rarity requires-attunement? attunement-detail attunement-by] :as detail-props} (reduce-kv #(assoc %1 %2 (get results %3)) {} detail-fields)
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
          (assoc obj field (reduce #(assoc %1 (get enum-map %2) true) {} ks)))))))

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

(defn append-node-to-compendium [node compendium [category compendium-key-fn spec-obj-fn]]
  (let [obj (spec-obj-fn (node->object node))
        key (compendium-key-fn obj)]
    (if (contains? compendium category)
      (update compendium category assoc key obj)
      (assoc compendium category (assoc (sorted-map) key obj)))))

(defn parse-split-list [obj delim old-key new-key]
  (if (empty? (get obj old-key))
    obj
    (let [value (get obj old-key)
          values (str/split value delim)]
      (dissoc (assoc obj new-key values) old-key))))

(defn parse-ability [delim obj]
  (let [{:keys [abilities] :as obj} (parse-split-list obj delim :ability :abilities)
        abilities (into (sorted-map) (mapv #(str/split % #"\s") abilities))]
    (assoc obj :abilities abilities)))

(defn parse-proficiency [delim obj]
  (parse-split-list obj delim :proficiency :proficiencies))

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
          text (->> (if (vector? text) text [text])
                    (reduce #(concat %1 (str/split %2 #"\n")) [])
                    (remove empty?))
          find-source #(str/starts-with? % "Source: ")
          sources (map #(str/replace % "Source: " "") (filter find-source text))
          sources (reduce #(concat %1 (str/split %2 #", ")) [] sources)
          text (full-trim (str/join " " (remove find-source text)))]
      (merge
        (if (empty? text)
          (dissoc obj text-key)
          (assoc obj text-key text))
        (if (empty? sources) {} {:sources sources} )))))

(def parse-text-and-source (partial parse-source :text))

(def parse-desc-and-source (partial parse-source :description))

(defn reduce-to-text [obj]
  (let [obj (update obj :text full-trim)]
    (if (= (set (keys obj)) #{:name :text})
      (:text obj)
      (dissoc obj :name))))

(def reduce-trait (comp (partial parse-proficiency #", ") reduce-to-text))

(defn parse-trait-type [old-key new-key value-fn]
  (fn [obj]
    (if (empty? (old-key obj))
      obj
      (let [traits (old-key obj)
            traits (if (vector? traits) traits [traits])
            traits (reduce
                     #(assoc %1 (:name %2) (value-fn %2))
                     {} traits)]
        (dissoc (assoc obj new-key traits) old-key)))))

(def trait->traits (parse-trait-type :trait :traits reduce-trait))

(def action->actions (parse-trait-type :action :actions reduce-trait))

(def reaction->reactions (parse-trait-type :reaction :reactions reduce-trait))

(defn parse-legendary [obj]
  ; TODO
  obj)

(def ability-scores [:str :dex :con :wis :int :cha])

(def stats [:passive :ac :speed :hp :cr])

(def bonuses [:skill :save :immune :conditionImmune :resist :vulnerable :senses])

(def demographics [:type :alignment :environment :languages :size])

(defn pull-fields [fields prop obj]
  (let [group (select-keys obj fields)]
    (if (empty? group)
      obj
      (apply dissoc (assoc obj prop group) fields))))

(def parsers
  {:float #(Double/parseDouble %)
   :int #(Long/parseLong %)})

(defn parse-numbers [& {:as fields}]
  (fn [obj]
    (let [key-set (set/intersection (set (keys fields)) (set (keys obj)))]
      (reduce
        #(update %1 %2 (get parsers %2 identity))
        obj key-set))))

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
                         parse-modifier
                         (un-abbreviate-enum :size sizes)
                         (partial parse-ability #", ")
                         (partial parse-proficiency #", ")
                         trait->traits)]
   :class [:classes :name identity]
   :feat [:feats :name (comp parse-text-and-source (partial parse-proficiency #", ") parse-modifier)]
   :background [:background :name (comp (partial parse-proficiency #", ") trait->traits)]
   :spell [:spells :name (comp
                           parse-text-and-source
                           (un-abbreviate-enum :school schools-of-magic)
                           )]
   :monster [:monsters :name (comp
                               parse-desc-and-source
                               trait->traits
                               action->actions
                               reaction->reactions
                               (un-abbreviate-enum :size sizes)
                               (partial pull-fields ability-scores :abilityScores)
                               (partial pull-fields stats :stats)
                               (partial pull-fields bonuses :bonuses)
                               (partial pull-fields demographics :demographics)
                               parse-legendary)]})

(defn get-choices [tag]
  (let [choice (choices tag)]
    (if (vector? choice)
      #(append-node-to-compendium %1 %2 choice)
      choice)))

(defn- append-to-compendium [zipped compendium]
  (let [content (:content (first (remove empty? zipped)))
        content (if (empty? content) [] (if (vector? content) content [content]))]
    (reduce #(let [choice (get-choices (:tag %2))] (choice %2 %1)) compendium content)))

(defn- pre-de-code-xml-text [xml-text]
  (let [outval (str/escape
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
                  (char 19) ""})]
    outval))

(defn- unzip-xml [xml-file]
  (zip/xml-zip
    (xml/parse
      (ByteArrayInputStream.
        (.getBytes
          (pre-de-code-xml-text
            (slurp xml-file))
          StandardCharsets/UTF_8
          )))))

(defn- parse-folder [file compendium]
  (if (.isDirectory file)
    (reduce #(append-to-compendium (unzip-xml %2) %1) compendium (.listFiles file))
    (append-to-compendium (unzip-xml file) compendium)))

(defn process [source dest]
  (let [json (parse-folder source {})]
    (spit
      dest
      (json/generate-string
        json
        {:pretty true}))))
