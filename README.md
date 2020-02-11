# TokenscriptViewer
Standalone TokenScript web viewer for reference

To Run:

From Intellij GUI: 
  click 'Gradle'->'tokenscriptviewer'->Tasks->application->bootRun

From commandline: 
  > gradlew bootRun
  
For default tokenscript demo just go to localhost:8080

To view different tokens use:

```localhost:8080?token=<Token Address>&tokenId=<TokenId Hex>&chainId=<Eth chain eg 1 for main net>```
  
EG:

```localhost:8080?token=0xec78db1c7244854420a2d8d8d8349c646ac60e06&tokenId=32303234303730363231303030302b30333030010200434e00554b0101000300&chainId=100```
