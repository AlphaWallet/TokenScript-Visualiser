proc lmap args {
    set body [lindex $args end]
    set args [lrange $args 0 end-1]
    set n 0
    set pairs [list]
    foreach {varname listval} $args {
        upvar 1 $varname var$n
        lappend pairs var$n $listval
        incr n
    }
    set temp [list]
    foreach {*}$pairs {
        lappend temp [uplevel 1 $body]
    }
    set temp
}
