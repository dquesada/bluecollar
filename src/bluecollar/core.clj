(ns bluecollar.core
  "The core namespace for the bluecollar library. 
  Use bluecollar.core to setup and teardown bluecollar.

  In order to setup bluecollar first create two hash maps. The hash maps will contain
  the queue specifications and the worker specifications.

  As stated above, the queue specifications is a hash map.
  Each keyword in the hash map will be used as a queue name.
  Each value will determine the size of the thread pool backing the respective queue.
  It can contain any number of arbitrarily named queues.

  In this example there are 3 queue specifications:

  => {\"high-importance\" 10 \"medium-importance\" 5 \"catch-all\" 5}

  The worker specifications is also a hash map.
  Each keyword in the hash map will represent a unique worker (later this is how the worker can be referenced to enqueue jobs).
  The value for each worker specification is a hash map containing 3 required things:
    1.) The queue it should be placed on in order to be processed.
    2.) The namespace and function it should execute when being processed.
    3.) The ability to retry if the job given to the worker results in an exception.

  In this example there are 2 worker specifications:

  => { :worker-one {:fn clojure.core/+, :queue \"high-importance\", :retry true}
       :worker-two {:fn nick.zalabak/blog, :queue \"catch-all\", :retry false} }

  In order to start bluecollar:

  => (use 'bluecollar.core)
  => (def queue-specs {\"high-importance\" 10 \"medium-importance\" 5 \"catch-all\" 5})
  => (def worker-specs {:worker-one {:fn clojure.core/+, :queue \"high-importance\", :retry true}
                        :worker-two {:fn nick.zalabak/blog, :queue \"catch-all\", :retry false}})
  => (bluecollar-setup queue-specs worker-specs)

  Optionally, bluecollar-setup accepts a third hash-map. The third hash-map contains connection
  details for Redis. Most likely you aren't running Redis on the same server you're running this
  application. In that scenario you'll need to provide the details on the hostname, port, db,
  timeout, and namespace. Namespace is purely a naming convention prefix associated with all of the
  data structures stored in Redis.
  Here is an example using an alternative hostname, port, db, timeout, and namespace:

  => (def redis-settings {:redis-namespace \"whitecollar\",
                          :redis-hostname \"redis-master.dc1.com\",
                          :redis-port 1234,
                          :redis-db 6,
                          :redis-timeout 6000})

  In order to safely shut down bluecollar:

  => (bluecollar-teardown)

  "
  (:use bluecollar.lifecycle
        bluecollar.properties)
  (:require [bluecollar.job-sites :as job-site]
            [bluecollar.redis :as redis]
            [bluecollar.union-rep :as union-rep]
            [clojure.tools.logging :as logger]))

(def job-sites (atom []))

(defn bluecollar-setup
  "Setup and start bluecollar by passing it the specifications for both the
   queues and workers."
  ([queue-specs worker-specs] (bluecollar-setup queue-specs worker-specs {:redis-hostname "127.0.0.1",
                                                                          :redis-port 6379,
                                                                          :redis-db 0,
                                                                          :redis-timeout 5000}))
  ([queue-specs worker-specs {redis-namespace :redis-namespace
                              redis-hostname :redis-hostname
                              redis-port :redis-port
                              redis-db :redis-db
                              redis-timeout :timeout}]
    (logger/info "Bluecollar setup is beginning...")
    (reset! redis/redis-namespace (or redis-namespace "bluecollar"))
    (redis/startup {:host (or redis-hostname "127.0.0.1")
                    :port (or redis-port 6379)
                    :db (or redis-db 0)
                    :timeout (or redis-timeout 5000)})
    (doseq [[worker-name worker-defn] worker-specs]
      (union-rep/register-worker worker-name (struct union-rep/worker-definition
        (:fn worker-defn)
        (:queue worker-defn)
        (:retry worker-defn))))
    (doseq [[queue-name pool-size] queue-specs]
      (swap! job-sites conj (job-site/new-job-site queue-name pool-size)))
    (doseq [site @job-sites] (startup site))))

(defn bluecollar-teardown
  "Shut down bluecollar"
  []
  (logger/info "Bluecollar is being torn down...")
  (if-not (empty? @job-sites)
    (do
      (doseq [site @job-sites] (shutdown site))
      (reset! union-rep/registered-workers {})
      (reset! job-sites [])
      (redis/shutdown))))
