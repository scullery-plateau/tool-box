(ns tool-box.fight-club-five-test
  (:require [clojure.test :refer :all]
            [tool-box.fight-club-five :as fc5]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp])
  (:import (java.io File)))

(deftest run-fight-club-five-process-items-llk
  (fc5/process
    (io/file "resources/fc5/Sources/LostLaboratoryOfKwalish/items-llk.xml")
    (io/file "resources/test-dest/items-llk.json")))

(def exclude
  ["UnearthedArcana"
   "Miscellaneous"
   "Homebrew"])

(def exclusions-with-errors
  ["AcquisitionsIncorporated/vehicles-ai.xml"
   "ExplorersGuideToWildemount/races-egw.xml"
   "GhostsOfSaltmarsh/vehicles-gos.xml"
   "GuildmastersGuideToRavnica/races-ggr.xml"
   "RiseOfTiamat/bestiary-rot.xml"
   "ThirdParty/TomeOfBeasts.xml"
   ])

(def safe-exclusions
  [
   "LostLaboratoryOfKwalish/items-llk.xml"
   "StormLordsWrath/bestiary-slw.xml"
   "SystemReferenceDocument/all-srd.xml"
   "ThirdParty/CreatureCodex.xml"
   "VolosGuideToMonsters/races-vgm.xml"
   ])

(def exclusions (concat exclude safe-exclusions exclusions-with-errors))

(deftest run-fight-club-five-process-and-split-fc5-all
  (fc5/process-and-split
    (io/file "resources/fc5/Sources")
    (io/file "resources/test-dest/compendium")
    :exclude exclusions))

(deftest run-process-for-each-safe-exclude
  (let [errors (reduce
                 (fn [errors path]
                   (let [src (io/file "resources/fc5/Sources" path)
                         dest-name (str/replace (.getName src) ".xml" ".json")
                         dest (io/file "resources/test-dest" dest-name)]
                     (try
                       (fc5/process src dest)
                       (assoc errors dest-name false)
                       (catch Throwable t
                         (assoc errors dest-name {:message (.getMessage t)
                                                  :type (type t)})))))
                 {}
                 safe-exclusions)]
    (pp/pprint errors)))

(deftest run-process-for-each-exclude-with-errors
  (let [errors (reduce
                 (fn [errors path]
                   (let [src (io/file "resources/fc5/Sources" path)
                         dest-name (str/replace (.getName src) ".xml" ".json")
                         dest (io/file "resources/test-dest" dest-name)]
                     (try
                       (fc5/process src dest)
                       (assoc errors dest-name false)
                       (catch Throwable t
                         (assoc errors dest-name {:message (.getMessage t)
                                                  :type (type t)})))))
                 {}
                 exclusions-with-errors)]
    (pp/pprint errors)))
