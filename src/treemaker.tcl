
proc table {pathname nodes args} {
    global NS
    foreach col $args {
        if {[llength $col] != 3} {
            exit "Column description should have exactly 3 items"
        }
    }
    
    ttk::treeview $pathname -columns [lmap col $args {lindex $col 0}]
    foreach col $args {
        $pathname heading [lindex $col 0] -text [lindex $col 1]
    }

    return [lmap node $nodes {
        if [$node hasAttribute name] {
            set name [$node getAttribute name]
        } else {
            set name  ""
        }
        set values [lmap col $args {
            $node selectNodes -namespace $NS [lindex $col 2]
        }]
        $pathname insert {} end -text $name -values $values
    }]
}


