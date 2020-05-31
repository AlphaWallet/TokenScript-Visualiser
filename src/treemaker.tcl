# take a list of n nodes, create a table of n rows for each node,
# extract columns of data by the columns defined in $args.

# $args: each column is defined as a 3-element list of
#        {id, table-heading, xpath for value extraction}

# note that variable $name can be used in the xpath which, for each
# row, will be replaced by the @name value of the node of that row.

proc table {pathname nodes args} {

    foreach col $args {
        if {[llength $col] != 3} {
            puts "Column description should have exactly 3 items: $col"
            exit 255
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
            $node selectNodes [lindex $col 2]
        }]
        $pathname insert {} end -text $name -values $values
    }]
}


