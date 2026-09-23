# Emulia

Emulator for Adam & Eva of FRITZ! aka AVM devices.

Emulia is not related to FRITZ! aka AVM.

If you like the program, I would be happy to receive a donation.

### Environments
 - Don't forget to share anonymized environments with DieFlashe.
 - Save your own environment dumps as `Conf/np/*.txt`.

### Firewall
 - 5035 udp - broadcast receiver
 - 21 tcp - ftp control channel
 - 30000-49999 tcp - ftp data channels

### Requirements
 - You need to have installed Java in your `$PATH` aka `%PATH%`.
 - Root permissions to open port 21 are mandatory (it will not work without).

### Usage
 - Run `./go.sh DEVICE` on Linux or `go.bat DEVICE` on Windows to start.
 - This uses your environment file `Conf/DEVICE.txt` or `Conf/np/DEVICE.txt`.
 - Without `DEVICE` the file `Conf/_Generic.txt` is used (don't try this).

### Output
 - Session log files are saved to `./Logs/`.
 - Transmitted blobs are saved to `./Bins/`.

### Insights
 - Collected [Environments](https://github.com/DieFlashe/emulia/tree/trunk/Conf)
 - Known [HWSubRevisions](hwsubr.md)

### Links
 - [Sources](https://github.com/DieFlashe/emulia)
 - [Discussions](https://github.com/orgs/DieFlashe/discussions)
 - [Github](https://github.com/DieFlashe/)

