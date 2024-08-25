(ns tool-box.core-test
  (:require [clojure.test :as t]
            [tool-box.core :refer :all]))

(t/deftest stump-test
  (t/testing "stump test"
    (t/is (= 0 1))))