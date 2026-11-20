#!/system/bin/sh
# The PocketTk logo as plain ASCII for the flash screen. Source it, then:  pockettk_logo ui_print
# ASCII only on purpose: box-drawing and block characters come from a fallback font on phones and do not line up.
pockettk_logo() {
  _say="${1:-echo}"
  $_say "        .----------------."
  $_say "        |                |"
  $_say "        |  #####  #  #   |"
  $_say "        |    #    # #    |"
  $_say "        |    #    ##     |"
  $_say "        |    #    # #    |"
  $_say "        |    #    #  #   |"
  $_say "   .----'----------------'----."
  $_say "   | - - - - - - - - - - - -  |"
  $_say "   |                          |"
  $_say "   |     P o c k e t T k      |"
  $_say "   '--------------------------'"
  $_say ""
}
