# phase 003 server startup

The Forge 1.20.1 dedicated server startup gate ran on node 1 with Java 17 using the phase branch classes and resources.

The exact disposable runtime was `/mnt/hermes/projects/FutureShops/run`. Its `eula.txt` contained `eula=true`, and its isolated server port was `25575`.

The server reached the ready state and reported `Done (13.875s)! For help, type "help"`. FutureShops loaded its default shop, generated and loaded the Bazaar catalog, completed legacy wallet migration, and entered escrow recovery without a crash. No client, renderer, or display process was started.

The server process exited after the startup assertion. The disposable `run` directory and its generated world, logs, configuration, and cache files were removed after the final consumer. No test-owned server resources remain.
