(ns codesmith.blocks.postgres
  (:require [codesmith.blocks :as cb]
            [codesmith.blocks.config :as cbc]
            [integrant.core :as ig]
            [next.jdbc.connection :as conn])
  (:import [javax.sql DataSource]
           [org.flywaydb.core Flyway]
           [com.zaxxer.hikari HikariDataSource]))

(defn migrate-db! [^DataSource ds]
  (let [^Flyway flyway (-> (Flyway/configure) (.dataSource ds) (.load))]
    (.migrate flyway)))

(defn config->db-spec [{:keys [connection-url]}]
  {:jdbcUrl connection-url})

(defmethod cb/typed-block-transform
  [::cb/postgres :external]
  [block-key {:keys [application environment]} ig-config final-substitution]
  [(if (not (::external ig-config))
     (let [connection-url-key [::cbc/secret ::connection-url]]
       (assoc ig-config
         ::external {:connection-url (ig/ref connection-url-key)}
         connection-url-key {:application    application
                             :environment    environment
                             :block-name     (keyword (name block-key))
                             :parameter-name :connection-url}))
     ig-config)
   final-substitution])

(defmethod ig/init-key ::external
  [_ config]
  (let [^HikariDataSource ds (conn/->pool HikariDataSource (config->db-spec config))]
    (migrate-db! ds)
    ds))

(derive ::external ::cb/postgres)
