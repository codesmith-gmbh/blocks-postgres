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

(defn config->db-spec [{:keys [connection-url password]}]
  {:jdbcUrl    connection-url
   :password   password
   :autoCommit false})

(defmethod cb/typed-block-transform
  [::postgres :external]
  [block-key {:keys [application environment]} ig-config final-substitution]
  [(if (not (::external ig-config))
     (let [connection-url-key [::cbc/config ::connection-url]
           password-key       [::cbc/secret ::password]
           block-name         (keyword (name block-key))
           config-base        {:application application
                               :environment environment
                               :block-name  block-name}]
       (assoc ig-config
         ::external {:connection-url (ig/ref connection-url-key)
                     :password       (ig/ref password-key)}
         connection-url-key (assoc config-base :parameter-name :connection-url)
         password-key (assoc config-base :parameter-name :password)))
     ig-config)
   final-substitution])

(defmethod ig/init-key ::external
  [_ config]
  (let [^HikariDataSource ds (conn/->pool HikariDataSource (config->db-spec config))]
    (migrate-db! ds)
    ds))

(derive ::external ::postgres)
