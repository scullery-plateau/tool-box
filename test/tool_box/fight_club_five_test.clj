(ns tool-box.fight-club-five-test
  (:require [clojure.test :refer :all]
            [tool-box.fight-club-five :as fc5]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp])
  (:import (java.io File)))

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
   "ThirdParty/TomeOfBeasts.xml"])

(def safe-exclusions
  ["LostLaboratoryOfKwalish/items-llk.xml"
   "StormLordsWrath/bestiary-slw.xml"
   "SystemReferenceDocument/all-srd.xml"
   "ThirdParty/CreatureCodex.xml"
   "VolosGuideToMonsters/races-vgm.xml"])

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

(deftest run-fight-club-five-process-phb-items-base
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/items-base-phb.xml")
    (io/file "resources/fc5json/items-base-phb.json")))

(deftest run-fight-club-five-process-phb-items
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/items-phb.xml")
    (io/file "resources/fc5json/items-phb.json")))

(deftest run-fight-club-five-process-phb-backgrounds
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/backgrounds-phb.xml")
    (io/file "resources/fc5json/backgrounds-phb.json")))

(deftest run-fight-club-five-process-phb-feats
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/feats-phb.xml")
    (io/file "resources/fc5json/feats-phb.json")))

(deftest run-fight-club-five-process-phb-spells
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/spells-phb.xml")
    (io/file "resources/fc5json/spells-phb.json")))

(deftest run-fight-club-five-process-phb-races
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/races-phb.xml")
    (io/file "resources/fc5json/races-phb.json")))

(deftest run-fight-club-five-process-phb-bestiary
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/bestiary-phb.xml")
    (io/file "resources/fc5json/bestiary-phb.json")))

(deftest run-fight-club-five-process-phb-class-bard
  (fc5/process
    (io/file "resources/fc5/Sources/PlayersHandbook/class-bard-phb.xml")
    (io/file "resources/fc5json/class-bard-phb.json")))

(deftest run-fight-club-five-process-bestiary-mm
  (fc5/process
    (io/file "resources/fc5/Sources/MonsterManual/bestiary-mm.xml")
    (io/file "resources/fc5json/bestiary-mm.json")))

(deftest run-fight-club-five-process-items-mm
  (fc5/process
    (io/file "resources/fc5/Sources/MonsterManual/items-mm.xml")
    (io/file "resources/fc5json/items-mm.json")))


(deftest run-fight-club-five-process-srd
  (fc5/process
    (io/file "resources/fc5/Sources/SystemReferenceDocument/all-srd.xml")
    (io/file "resources/fc5json/all-srd.json")))

(deftest run-fight-club-five-process-items-llk
  (fc5/process
   (io/file "resources/fc5/Sources/LostLaboratoryOfKwalish/items-llk.xml")
   (io/file "resources/test-dest/items-llk.json")))

