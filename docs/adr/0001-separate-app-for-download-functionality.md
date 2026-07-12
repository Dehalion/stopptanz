# Keep download functionality as a separate app, not a v2 feature of the player

The stop-dance player only ever touches local files the user already has; a download feature (planned for later) pulls in ToS/Play-Store-policy risk of a very different kind. Bundling it risks the whole player app getting pulled over one feature. Decision: ship it as a separate app that writes into a folder the player's folder-picker can already read, keeping the player's review risk low.
