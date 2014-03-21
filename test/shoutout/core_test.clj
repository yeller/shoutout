(ns shoutout.core-test
  (:require [clojure.test :refer :all]
            [shoutout :refer :all :reload true]))

(deftest parse-feature-test
  (testing "groups"
    (testing "an empty feature has no groups"
      (is (= (:groups (parse-feature "an-feature" "||"))
             [])))

    (testing "an feature can have an group"
      (is (= (:groups (parse-feature "an-feature" "||admin"))
             ["admin"])))

    (testing "an feature can have an groups"
      (is (= (:groups (parse-feature "an-feature" "||admin,client"))
             ["admin" "client"]))))

  (testing "users"
    (testing "an empty feature has no users"
      (is (= (:users (parse-feature "an-feature" "||"))
             [])))

    (testing "an feature can have an user"
      (is (= (:users (parse-feature "an-feature" "|1|"))
             ["1"])))

    (testing "an feature can have an users"
      (is (= (:users (parse-feature "an-feature" "|1,2,3|"))
             ["1" "2" "3"]))))

  (testing "percentage"
    (testing "an empty feature has a percentage of 0"
      (is (= (:percentage (parse-feature "an-feature" "||"))
             0)))

    (testing "an feature can have an percentage"
      (is (= (:percentage (parse-feature "an-feature" "89||"))
             89)))))
