load ./chilkat.so

# The Chilkat API can be unlocked for a fully-functional 30-day trial by passing any
# string to the UnlockBundle method.  A program can unlock once at the start. Once unlocked,
# all subsequently instantiated objects are created in the unlocked state. 
# 
# After licensing Chilkat, replace the "Anything for 30-day trial" with the purchased unlock code.
# To verify the purchased unlock code was recognized, examine the contents of the LastErrorText
# property after unlocking.  For example:
set glob [new_CkGlobal]

set success [CkGlobal_UnlockBundle $glob "ALPHWL.CB1062021_97x7UY2A6R6m"]
if {$success != 1} then {
    puts [CkGlobal_lastErrorText $glob]
    delete_CkGlobal $glob
    exit
}

set status [CkGlobal_get_UnlockStatus $glob]
if {$status == 2} then {
    # puts "Unlocked using purchased unlock code."
} else {
    puts "Chilkat library unlocked in trial mode."
    # The LastErrorText can be examined in the success case to see if it was unlocked in
    # trial more, or with a purchased unlock code.

    puts [CkGlobal_lastErrorText $glob]
}

delete_CkGlobal $glob
