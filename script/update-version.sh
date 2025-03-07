#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
( \
cd "${DIR}/../" \
&& \
mvn org.eclipse.tycho:tycho-versions-plugin:set-version \
-P internal \
-Dartifacts=\
ru.taximaxim.codekeeper.cli,\
ru.taximaxim.codekeeper.rcp.product,\
ru.taximaxim.codekeeper.standalone.product,\
ru.taximaxim.codekeeper.mainapp,\
ru.taximaxim.codekeeper.feature \
-DnewVersion=$1"-SNAPSHOT" \
versions:set-property \
-Dproperty=revision \
-DgenerateBackupPoms=false \
)
