(ns codesmith.blocks.postgres
  (:require [codesmith.blocks :as cb]
            [codesmith.blocks.config]
            [integrant.core :as ig]
            [next.jdbc.connection :as conn]
            [migratus.core :as migratus])
  (:import [javax.sql DataSource]
           [com.zaxxer.hikari HikariDataSource]))

(defn migrate-db! [^DataSource ds]
  (let [config {:db {:datasource ds}}]
    (migratus/init config)
    (migratus/migrate config)))

(defn config->db-spec [{:keys [connection-url]}]
  {:jdbcUrl               connection-url
   :autoCommit            false
   :reWriteBatchedInserts true})

(defmethod cb/typed-block-transform
  [::cb/postgres :external]
  [block-key {:keys [application environment]} ig-config]
  (if (not (::external ig-config))
    (let [connection-url-key [::cb/secret ::connection-url]]
      (assoc ig-config
        ::external {:connection-url (ig/ref connection-url-key)}
        connection-url-key {:application    application
                            :environment    environment
                            :block-name     (keyword (name block-key))
                            :parameter-name :connection-url}))
    ig-config))

(defmethod ig/init-key ::external
  [_ config]
  (let [^HikariDataSource ds (conn/->pool HikariDataSource (config->db-spec config))]
    (migrate-db! ds)
    ds))

(derive ::external ::cb/postgres)
