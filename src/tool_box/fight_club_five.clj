(ns tool-box.fight-club-five
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s])
  (:import (java.io ByteArrayInputStream)))

(def root (io/file "resources/fc5"))

(def dest (io/file "resources/compendium.json"))

(defn node->object [{:keys [attrs content]} & {:keys [exclude]}]
  (let [attrs (or attrs {})
        children (filter map? content)
        children (filter #(contains? exclude (:tag %)) children)
        text (s/join "/n" (remove empty? (filter string? content)))]
    (if (and (empty? attrs) (empty? children))
      text
      (reduce #(assoc %1 (:tag %2) (node->object %2)) (assoc (apply dissoc attrs exclude) :_text text) children))))

(defn append-item-to-compendium [item-node compendium]
  (let [item (node->object item-node)

        ]
    (if (:items compendium)
      (update compendium :items assoc (:name item) item)
      (assoc-in compendium [:items (:name item)] item))))

(defn append-race-to-compendium [race-node compendium]
  )

(defn append-class-to-compendium [class-node compendium]
  )

(defn append-feat-to-compendium [feat-node compendium]
  )

(defn append-background-to-compendium [background-node compendium]
  )

(defn append-spell-to-compendium [spell-node compendium]
  )

(defn append-monster-to-compendium [monster-node compendium]
  )

(def choices
  {:item append-item-to-compendium
   :race append-race-to-compendium
   :class append-class-to-compendium
   :feat append-feat-to-compendium
   :background append-background-to-compendium
   :spell append-spell-to-compendium
   :monster append-monster-to-compendium})

(defn- append-to-compendium [zipped compendium]
  (reduce
    #((choices (:tag %2)) %2 %1)
    compendium
    (:content zipped)))

(defn- unzip-xml [xml-file]
  (zip/xml-zip
    (xml/parse
      (ByteArrayInputStream.
        (.getBytes
          (slurp xml-file))))))

(defn- parse-folder [file compendium]
  (if (.isDirectory file)
    (reduce #(append-to-compendium (unzip-xml %2) %1) compendium (.listFiles file))
    (append-to-compendium (unzip-xml file) compendium)))

(defn process [source dest]
  (spit dest
    (json/generate-string
      (parse-folder source {})
      {:pretty true})))
