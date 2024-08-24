(ns tool-box.outfitter-datasets 
  (:require [clj-http.client :as client]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(def body-types [:fit :hulk :superman :woman])

(def version [0 0 1])

(def path "https://scullery-plateau.github.io/apps/outfitter/datasets/%s.json")

(defn get-dataset [body-type-index]
  (client/get (format path (str/join "." (cons (name (nth body-types body-type-index)) version))) {:accept :json}))