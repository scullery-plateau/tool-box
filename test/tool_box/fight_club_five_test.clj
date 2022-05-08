(ns tool-box.fight-club-five-test
  (:require [clojure.test :refer :all]
            [tool-box.fight-club-five :as fc5]))

(deftest run-fight-club-five-process
  (fc5/process fc5/root fc5/dest))
