(ns moomoo-frontend.server-interface
  (:require [moomoo-frontend.core :as core]
            [moomoo-frontend.tracks :as tracks]
            [moomoo-frontend.app-state :as app-state]
            [moomoo-frontend.player :as player]
            [clojure.string :as string]))

(.on core/socket "sign-in-success"
  (fn []
    (println "Successfully signed in!")
    (swap! app-state/app-state assoc :signed-in? true)))

(.on core/socket "chat-message"
  (fn [message]
    (println "Received chat-message signal:" message)
    (swap! app-state/app-state assoc :messages (conj (:messages @app-state/app-state) message))
    (swap! app-state/app-state assoc :message-received? true)))

(.on core/socket "users-list"
  (fn [users]
    (println "Received users-list signal: " users)
    (let [users (js->clj users)]
      (swap! app-state/app-state assoc :users users))))

(.on core/socket "file-upload-info"
  (fn [file-upload-info]
    (if (= (.-totalsize file-upload-info) (.-bytesreceived file-upload-info))
      (swap! app-state/app-state assoc :current-uploads-info
        (dissoc (:current-uploads-info @app-state/app-state) (.-id file-upload-info)))
      (swap! app-state/app-state assoc :current-uploads-info
        (merge (:current-uploads-info @app-state/app-state)
          {(.-id file-upload-info) file-upload-info})))))

(.on core/socket "resume" player/resume!)

(.on core/socket "pause"
  (fn [position]
    (player/pause!)
    (player/set-position! position)))

(.on core/socket "position-change" player/set-position!)

(.on core/socket "clear-songs" core/clear-tracks!)

(.on core/socket "delete-track" tracks/delete-track!)

(.on core/socket "start-track"
  (fn [file-url position]
    (let [file-url (str (first (string/split (.-href (.-location js/window))
                                             #"/rooms"))
                        file-url)]
      (player/play-track! file-url
                          (:current-sound-id @app-state/app-state)
                          position
                          core/on-finish))))

(.on core/socket "track-change"
  (fn [track-id sound-id]
    (println "Received track-change signal:"
             "track-id:" track-id
             "sound-id:" sound-id)
    (let [last-current-track-id (:current-track-id @app-state/app-state)
          last-current-sound-id (:current-sound-id @app-state/app-state)]
      (swap! app-state/app-state assoc :current-track-id track-id)
      (swap! app-state/app-state assoc :current-sound-id sound-id)

      (if-not (nil? last-current-sound-id)
        (player/destroy-track last-current-sound-id)))

      (.emit core/socket "ready-to-start" sound-id)))

(.on core/socket "hotjoin-music-info"
  (fn [room-track-id-map
       room-music-info
       track-order
       current-track-id
       current-sound-id
       paused?]
    (println "Received room state:"
             "room-music-info:" room-music-info
             "track-order:" track-order
             "current-track-id:" current-track-id
             "current-sound-id:" current-sound-id
             "track-id-hashes:" room-track-id-map)

    (swap! app-state/app-state assoc :track-id-hashes (js->clj room-track-id-map))
    (swap! app-state/app-state assoc :track-order (js->clj track-order))
    (swap! app-state/app-state assoc :music-info (vec (map #(clj->js %1) (js->clj room-music-info))))

    (swap! app-state/app-state assoc :current-track-id current-track-id)
    (swap! app-state/app-state assoc :current-sound-id current-sound-id)

    (if paused?
      (player/pause!))

    (if-not (nil? current-sound-id)
      (.emit core/socket "ready-to-start" current-sound-id))))

(.on core/socket "set-loop"
  (fn [looping?]
    (println "Received set-loop signal with looping?:" looping?)
    (swap! app-state/app-state assoc :looping? looping?)))

(.oncore/socket "hash-found"
  (fn [file-hash]
    (println "File exists on server. Hash: " file-hash)
    (swap! app-state/app-state assoc :file-hashes (dissoc (:file-hashes @app-state/app-state) file-hash))))

(.oncore/socket "hash-not-found"
  (fn [file-hash]
    (println "File does not exist on server. Will upload. Hash: " file-hash)
    (let [file (get (:file-hashes @app-state/app-state) file-hash)]
      (swap! app-state/app-state assoc :file-hashes (dissoc (:file-hashes @app-state/app-state) file-hash))
      (core/upload-file file))))

(.oncore/socket "user-muted"
  (fn [socket-id]
    (println "Received mute-user signal for"socket-id)
    (swap! app-state/app-state assoc :users (merge (:users @app-state/app-state)
                                         {socket-id (merge (get (:users @app-state/app-state ) socket-id)
                                                           {"muted" true})}))))

(.oncore/socket "user-unmuted"
  (fn [socket-id]
    (println "Received umute-user signal for"socket-id)
    (swap! app-state/app-state assoc :users (merge (:users @app-state/app-state)
                                         {socket-id (merge (get (:users @app-state/app-state) socket-id)
                                                           {"muted" false})}))))

(.oncore/socket "upload-cancelled"
  (fn [id]
    (swap! app-state/app-state assoc :current-uploads-info
      (dissoc (:current-uploads-info @app-state/app-state) id))))

(.oncore/socket "track-order-change"
  (fn [track-order]
    (swap! app-state/app-state assoc :track-order (js->clj track-order))))
