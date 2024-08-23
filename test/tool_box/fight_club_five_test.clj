(ns tool-box.fight-club-five-test
  (:require [clojure.test :refer :all]
            [tool-box.fight-club-five :as fc5]
            [clojure.java.io :as io]))

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
