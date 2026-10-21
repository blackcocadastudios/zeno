# Smoke test

- ✓ install app-x86_64-release.apk
- ✓ home page shows the wordmark and the uBlock card
- ✓ tap Instalar on the uBlock card
- ✓ install dialog asks for permissions
- ✓ approve the install
- ✓ example.com from another app
- ✓ tap the address
- ✓ search results show the terms
- ✓ wikipedia opens
- ✓ the bar comes back when scrolling up
- ✓ pulling the page down reloads it
- ✓ the bar is there after the pull
- ✓ open the tab grid
- ✓ open the menu
- ✓ menu lists uBlock Origin
- ✓ new private tab
- ✓ open the menu again
- ✓ open settings

## Measurements

```
apk: app-x86_64-release.apk
first launch: TotalTime: 2727
cold start: TotalTime: 2315
cold start: WaitTime: 2321
warm start: TotalTime: 237
main process:            TOTAL PSS:   180835            TOTAL RSS:   236952       TOTAL SWAP PSS:    62728
Zeno processes (PSS, KB):
    242,721K: com.blackcocadastudios.zeno:tab_disable_art_image_38 (pid 5456)
    185,409K: com.blackcocadastudios.zeno (pid 4530 / activities)
    122,538K: com.blackcocadastudios.zeno:tab_disable_art_image_6 (pid 6439)
     91,103K: com.blackcocadastudios.zeno:tab_disable_art_image_37 (pid 6699)
     44,626K: com.blackcocadastudios.zeno:gpu_disable_art_image_ (pid 5451)
     39,791K: com.blackcocadastudios.zeno:tab_disable_art_image_15 (pid 5549)
      4,313K: com.blackcocadastudios.zeno:crashhelper_disable_art_image_ (pid 4609)
```

No crashes in logcat.
