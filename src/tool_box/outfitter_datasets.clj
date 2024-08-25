(ns tool-box.outfitter-datasets 
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.zip :as zip]) 
  (:import [java.io ByteArrayInputStream]))

(def body-types [:fit :hulk :superman :woman])

(def torso-tops 
  {:fit 106.35
   :hulk 169.1
   :superman 146.9
   :woman 93.6})

(def head-parts 
  #{:beard :ears :eyebrows :eyes :hair :hat :head :mask :mouth :nose})

(def part-groups
  #{"Body"
    "Face"
    "Tights"
    "Clothing"
    "Back"
    "Accessories"})

(def part-type-list
  {:accessories_and_shields ["Accessories" "Accessories & Shields"]
   :arm "Body"
   :back "Back"
   :beard "Face"
   :belt "Clothing"
   :boots "Clothing"
   :chest "Clothing"
   :collar "Clothing"
   :ears "Face"
   :eyebrows "Face"
   :eyes "Face"
   :gauntlets "Clothing"
   :gloves "Tights"
   :guns "Accessories"
   :hair "Face"
   :hat "Clothing"
   :head "Body"
   :legs "Body"
   :mask "Tights"
   :melee_weapons ["Accessories" "Melee Weapons"]
   :mouth "Face"
   :nose "Face"
   :pants "Clothing"
   :ranged_weapons ["Accessories" "Ranged Weapons"]
   :shirt "Tights"
   :sholders ["Clothing" "Shoulders"]
   :stockings "Tights"
   :swords "Accessories"
   :symbol_A ["Accessories" "Symbol A"]
   :symbol_B ["Accessories" "Symbol B"]
   :tights ["Tights" "Leggings"]
   :torso "Body"
   :wings_and_tails ["Back" "Wings & Tails"]})
 
(def version [0 0 1])

(def path "https://scullery-plateau.github.io/apps/outfitter/datasets/%s.json")

(defn get-dataset [body-type-index]
  (client/get (format path (str/join "." (cons (name (nth body-types body-type-index)) version))) {:accept :json}))

(defn xml->hiccup [node]
  (if (string? node)
    node
    (let [{ tag :tag attrs :attrs content :content} node
          header (if (nil? attrs) [tag] [tag attrs])]
      (if (nil? content)
        header
        (concat header (map xml->hiccup content))))))

(defn parse-svg [svg-text]
  (first
   (zip/xml-zip
    (xml/parse
     (ByteArrayInputStream. (.getBytes svg-text))))))