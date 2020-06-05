#!/usr/bin/tclsh
source global_unlock.tcl

proc verify-xmldsig {sbXml} {
    set dsig [new_CkXmlDSig]

    # First load the XML containing the signatures to be verified.
    set success [CkXmlDSig_LoadSignatureSb $dsig $sbXml]
    if {$success != 1} then {
        puts [CkXmlDSig_lastErrorText $dsig]
        delete_CkStringBuilder $sbXml
        delete_CkXmlDSig $dsig
        exit
    }

    # It's possible that an XML document can contain multiple signatures.
    # Each can be verified as follows:
    set i 0
    while {$i < [CkXmlDSig_get_NumSignatures $dsig]} {
        # Select the Nth signature by setting the Selector property.
        CkXmlDSig_put_Selector $dsig $i

        # The bVerifyReferenceDigests argument determines if we want
        # to also verify each reference digest.  If set to 0,
        # then only the SignedInfo part of the Signature is verified.
        set bVerifyReferenceDigests 1
        set bVerified [CkXmlDSig_VerifySignature $dsig $bVerifyReferenceDigests]
        puts -nonewline "Signature [expr $i + 1] verified = $bVerified"

        set i [expr $i + 1]
    }

    delete_CkStringBuilder $sbXml
    delete_CkXmlDSig $dsig
}


if {$argc == 0} {
    puts "Try $argv0 followed by one or more signed XML file."
}

foreach arg $argv {
    set sbXml [new_CkStringBuilder]
    set success [CkStringBuilder_LoadFile $sbXml $arg "utf-8"]
    if {$success != 1} then {
        puts "Failed to load XML file: $arg"
        delete_CkStringBuilder $sbXml
        exit
    }
    verify-xmldsig $sbXml
    puts " filename = $arg"
}
