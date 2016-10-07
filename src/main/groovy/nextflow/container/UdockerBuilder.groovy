/*
 * Copyright (c) 2013-2016, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2016, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.container

import groovy.transform.CompileStatic

/**
 * Wrap a task execution in a Udocker container
 *
 * See https://github.com/indigo-dc/udocker
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class UdockerBuilder extends ContainerBuilder {

    private String image

    private String temp

    private boolean remove = true

    private List<String> runOptions = []

    private String entryPoint = '/bin/bash'

    private String runCommand

    UdockerBuilder( String image ) {
        this.image = image
        if( !this.image.contains(":") )
            this.image += ':latest'
    }

    @Override
    ContainerBuilder params(Map params) {
        if( !params ) return this

        if( params.containsKey('temp') )
            this.temp = params.temp

        if( params.containsKey('runOptions') )
            addRunOptions(params.runOptions.toString())

        if ( params.containsKey('remove') )
            this.remove = params.remove?.toString() == 'true'

        if( params.containsKey('entry') )
            this.entryPoint = params.entry

        return this
    }

    UdockerBuilder addRunOptions(String str) {
        runOptions.add(str)
        return this
    }

    @Override
    UdockerBuilder build(StringBuilder result) {
        assert image, 'Missing container image'

        result << 'udocker.py '
        result << 'run '

        if( remove ) {
            result << '--rm '
        }

        if( cpus ) {
            result << "--cpuset-cpus=$cpus "
        }

        // add the environment
        for( def entry : env ) {
            result << makeEnv(entry) << ' '
        }

        if( temp )
            result << "-v $temp:/tmp "

        // mount the input folders
        result << makeVolumes(mounts)
        result << ' -w "$PWD" --bindhome '


        if( runOptions )
            result << runOptions.join(' ') << ' '

        // the ID of the container to run
        result << "\$(udocker.py create \"$image\") "

        // finally the entry point to execute eg. `/bin/bash`
        result << entryPoint

        runCommand = result.toString()
        return this
    }


    @Override
    String getRunCommand() { runCommand }

    @Override
    String getRemoveCommand() { null }

    StringBuilder appendCreateCommand( StringBuilder wrapper ) {
        wrapper << "(udocker.py images | egrep -o \"^$image\\s\") || udocker.py pull \"$image\"\n"
        wrapper << "[[ \$? != 0 ]] && echo \"Udocker failed to pull container \\`$image\\`\" >&2 && exit 1\n"
        wrapper << "udocker_cid=\$(udocker.py create \"$image\")\n"
        wrapper << "[[ \$? != 0 ]] && echo \"Udocker failed to create container \\`$image\\`\" >&2 && exit 1\n"
    }


    /**
     * Normalize Shifter image name adding `docker:` prefix or `:latest`
     * when required
     *
     * @param imageName The container image name
     * @param shifterConfig Shifter configuration map
     * @return Image name in Shifter canonical format
     */
    static String normalizeImageName( String imageName ) {

        if( !imageName )
            return null

        if( !imageName.contains(':') )
            imageName += ':latest'

        return imageName
    }

}
