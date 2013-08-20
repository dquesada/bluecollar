(ns bluecollar.client-test
  (:use clojure.test
        bluecollar.test-helper
        bluecollar.client)
  (:require [bluecollar.workers-union :as workers-union]
            [bluecollar.job-plans :as plan]
            [bluecollar.fake-worker]
            [bluecollar.redis :as redis]
            [bluecollar.keys-and-queues :as keys-qs]
            [clj-time.core :as time]))

(use-fixtures :each (fn [f]
  (reset! bluecollar.fake-worker/perform-called false)
  (workers-union/clear-registered-workers)
  (redis/shutdown)
  (bluecollar-client-setup [:hard-worker :worker-two] {:redis-key-prefix "fleur-de-sel"})
  (redis/flushdb)
  (f)
  (bluecollar-client-teardown)))

(deftest bluecollar-client-setup-test
  (testing "registers the worker specs"
    (is (not (empty? @workers-union/registered-workers))))

  (testing "sets the Redis connection"
    (is (= "PONG" (redis/ping))))

  (testing "sets an alternative redis-namespace"
    (is (= "fleur-de-sel" @keys-qs/prefix))))

(deftest bluecollar-client-teardown-test
  (testing "teardown works properly"
    (bluecollar-client-teardown)
    (is (empty? @workers-union/registered-workers))
    (is (= "bluecollar" @keys-qs/prefix))))

(deftest async-job-for-test
  (testing "successfully sends a job for a registered worker to process"
    (is (nil? (redis/pop-to-processing "crunch-numbers")))
    (is (not (nil? (re-find uuid-regex (async-job-for :hard-worker [1 3])))))
    (is (not (nil? (redis/pop-to-processing "crunch-numbers")))))

  (testing "successfully sends a job with a scheduled runtime"
    (is (nil? (redis/pop-to-processing "crunch-numbers")))
    (is (not (nil? (re-find uuid-regex (async-job-for :hard-worker [1 3] (str (time/now)))))))
    (is (not (nil? (redis/pop-to-processing "crunch-numbers")))))
  
  (testing "throws a RuntimeException when an unregistered worker is encountered"
    (let [_ (reset! bluecollar.workers-union/registered-workers {})]
      (is (thrown-with-msg? RuntimeException #":hard-worker was not found in the worker registry." (async-job-for :hard-worker [1 3])))
      )
    ))