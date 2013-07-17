(ns bluecollar.foreman-test
  (:use clojure.test
        bluecollar.lifecycle
        bluecollar.test-helper)
  (:require [bluecollar.foreman :as foreman]
            [bluecollar.job-plans :as plan]
            [bluecollar.fake-worker]
            [clj-time.core :as time]
            [bluecollar.workers-union :as workers-union]))

(def number-of-workers 5)

(use-fixtures :each (fn [f]
  (redis-setup)
  (workers-union/clear-registered-workers)
  (reset! bluecollar.fake-worker/perform-called false)
  (reset! bluecollar.fake-worker/fake-worker-failures 0)
  (f)))

(deftest new-foreman-test
  (testing "takes the number of workers as an argument"
    (is (not (nil? (foreman/new-foreman 5))))
    ))

(deftest foreman-start-stop-workers-test
  (testing "all of the workers can start and stop"
    ; new foreman
    (let [a-foreman (foreman/new-foreman number-of-workers)]
      (startup a-foreman)
      (is (= number-of-workers (foreman/worker-count a-foreman)))
      (is (= number-of-workers (foreman/scheduled-worker-count a-foreman)))
      (shutdown a-foreman)
      (Thread/sleep 1000)
      (is (= 0 (foreman/worker-count a-foreman)))
      (is (= 0 (foreman/scheduled-worker-count a-foreman))))
    ))

(deftest foreman-dispatch-worker-test
  (testing "assigns the server hostname to the job plan during dispatch"
    ;TODO fill this is
    )

  (testing "dispatches a worker based on a job plan"
    (let [workers {:fake-worker (workers-union/new-unionized-worker bluecollar.fake-worker/perform
                                                                 testing-queue-name 
                                                                 false)}
          _ (workers-union/register-workers workers)
          a-foreman (foreman/new-foreman number-of-workers)
          a-job-plan (plan/new-job-plan :fake-worker [1 2])]
      (do
        (startup a-foreman)
        (foreman/dispatch-worker a-foreman a-job-plan)
        (Thread/sleep 500)
        (is (true? (deref bluecollar.fake-worker/perform-called)))
        (shutdown a-foreman))
      )))

(deftest foreman-dispatch-scheduled-worker-test
  (testing "can dispatch a worker based on a scheduled job plan"
    (let [workers {:fake-worker (workers-union/new-unionized-worker bluecollar.fake-worker/perform
                                                                 testing-queue-name 
                                                                 false)}
          _ (workers-union/register-workers workers)
          a-foreman (foreman/new-foreman number-of-workers)
          a-job-plan (plan/new-job-plan :fake-worker [1 2] (str (time/plus (time/now) (time/secs 2))))]
      (do
        (startup a-foreman)
        (foreman/dispatch-scheduled-worker a-foreman a-job-plan)
        (Thread/sleep 3000)
        (is (true? (deref bluecollar.fake-worker/perform-called)))
        (shutdown a-foreman))
      )))

