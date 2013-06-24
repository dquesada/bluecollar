(ns bluecollar.client-test
  (:use clojure.test
        bluecollar.test-helper
        bluecollar.client)
  (:require [bluecollar.union-rep :as union-rep]
            [bluecollar.job-plans :as plan]
            [bluecollar.fake-worker]
            [bluecollar.redis :as redis]
            [clj-time.core :as time]))

(def worker-specs {:hard-worker {:fn bluecollar.fake-worker/perform, :queue "crunch-numbers", :retry false}
                   :worker-two {:fn bluecollar.fake-worker/explode, :queue "low-importance", :retry true}} )

(use-fixtures :each (fn [f]
  (reset! bluecollar.fake-worker/perform-called false)
  (redis/shutdown)
  (bluecollar-client-setup worker-specs {:redis-namespace "fleur-de-sel"})
  (redis/flushdb)
  (f)
  (bluecollar-client-teardown)))

(deftest bluecollar-client-setup-test
  (testing "registers the worker specs"
    (is (not (empty? @union-rep/registered-workers))))

  (testing "sets the Redis connection"
    (is (= "PONG" (redis/ping))))

  (testing "sets an alternative redis-namespace"
    (is (= "fleur-de-sel" @redis/redis-namespace))))

(deftest bluecollar-client-teardown-test
  (testing "teardown works properly"
    (bluecollar-client-teardown)
    (is (empty? @union-rep/registered-workers))
    (is (nil? @redis/redis-namespace))))

(deftest async-job-for-test
  (testing "successfully sends a job for a registered worker to process"
    (is (nil? (redis/pop "crunch-numbers")))
    (is (not (nil? (re-find uuid-regex (async-job-for :hard-worker [1 3])))))
    (is (not (nil? (redis/pop "crunch-numbers")))))

  (testing "successfully sends a job with a scheduled runtime"
    (is (nil? (redis/pop "crunch-numbers")))
    (is (not (nil? (re-find uuid-regex (async-job-for :hard-worker [1 3] (str (time/now)))))))
    (is (not (nil? (redis/pop "crunch-numbers")))))
  
  (testing "throws a RuntimeException when an unregistered worker is encountered"
    (let [_ (reset! bluecollar.union-rep/registered-workers {})]
      (is (thrown-with-msg? RuntimeException #":hard-worker was not found in the worker registry." (async-job-for :hard-worker [1 3])))
      )
    ))