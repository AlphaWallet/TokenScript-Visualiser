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

    if [winfo exists $pathname] { destroy $pathname }
    ttk::treeview $pathname -columns [lmap col $args {lindex $col 0}]
    
    foreach col $args {
        $pathname heading [lindex $col 0] -text [lindex $col 1]
    }

    return [lmap node $nodes {
        set name [expr {[$node hasAttribute name]?[$node getAttribute name]:""}]
        set values [lmap col $args {$node selectNodes [lindex $col 2]}]
        $pathname insert {} end -text $name -values $values
    }]
}


proc doc_open {} {
    global filename
    set file_types {
        { {TokenScript XML Files} { .xml .XML} }
        { {Signed TokenScrpt (TSML) Files} { .tsml TSML } }
        { {All Files} * }
    }

    if [info exist filename] {
        set new_filename [tk_getOpenFile -filetypes $file_types \
                              -initialdir [file dirname $filename] \
                              -initialfile [file tail $filename]]
    } else {
        set new_filename [tk_getOpenFile -filetypes $file_types]
    }
    if {$new_filename != ""} {
        # The user didn't press "cancel"
        if [catch {open $new_filename r} fileid] {
            # Error opening file -- with "catch", fileid holds error message
            tk_messageBox -icon error -message \
                "Couldn't open \"$filename\":\n$fileid"
        } else {
            # OK, didn't cancel, and filename is valid -- save it
            set filename $new_filename
            doc_load $fileid
            close $fileid
            wm title . $filename
        }
    }
}

# this procedure is called when a file is opened or supplied by commandline
proc doc_load {fileid} {
    global NS xp-attribute xp-card xp-dataObject xp-localisation
    set doc [dom parse [read $fileid]]

    $doc selectNodesNamespaces $NS
    set root [$doc documentElement]

    set nodes [$root selectNodes {//ts:attribute}]
    set rows [table .nb.f1.table $nodes {*}${xp-attribute}]
    .nb tab .nb.f1 -text "Token Attributes ([llength $rows])"

    set nodes [$root selectNodes {/ts:token/ts:cards/ts:card}]
    set rows [table .nb.f2.table $nodes {*}${xp-card}]
    .nb tab .nb.f2 -text "Cards ([llength $rows])"

    set nodes [$root selectNodes {//asnx:module}]
    set rows [table .nb.f3.table $nodes {*}${xp-dataObject}]
    .nb tab .nb.f3 -text "Data Objects ([llength $rows])"

    set nodes [$root selectNodes {//ts:label}]
    set rows [table .nb.f4.table $nodes {*}${xp-localisation}]
    .nb tab .nb.f4 -text "Strings ([llength $rows])"

    pack .nb.f1.table
    pack .nb.f2.table
    pack .nb.f3.table
    pack .nb.f4.table
}
