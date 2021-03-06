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

package nextflow.trace
import java.nio.file.Files
import java.nio.file.Paths

import groovyx.gpars.dataflow.DataflowQueue
import nextflow.dag.CytoscapeHtmlRenderer
import nextflow.dag.DAG
import nextflow.dag.DotRenderer
import nextflow.dag.GraphvizRenderer
import spock.lang.Requires
import spock.lang.Specification
import test.TestHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Mike Smoot <mes@aescon.com>
 */
class GraphObserverTest extends Specification {

    def DAG test_dag

    def setup() {

        def src = new DataflowQueue()
        def ch1 = new DataflowQueue()
        def ch2 = new DataflowQueue()
        def ch3 = new DataflowQueue()

        test_dag = new DAG()

        test_dag.addVertex(
                DAG.Type.ORIGIN,
                'Source',
                null,
                [ new DAG.ChannelHandler(channel: src, label: 'src') ] )

        test_dag.addVertex(
                DAG.Type.PROCESS,
                'Process 1',
                [ new DAG.ChannelHandler(channel: src, label: 'Source') ],
                [ new DAG.ChannelHandler(channel: ch1, label: 'Channel 1') ] )

        test_dag.addVertex(
                DAG.Type.OPERATOR,
                'Filter',
                [ new DAG.ChannelHandler(channel: ch1, label: 'Channel 1') ],
                [ new DAG.ChannelHandler(channel: ch2, label: 'Channel 2') ] )


        test_dag.addVertex(
                DAG.Type.PROCESS,
                'Process 2',
                [ new DAG.ChannelHandler(channel: ch2, label: 'Channel 2') ],
                [ new DAG.ChannelHandler(channel: ch3, label: 'Channel 3') ] )

    }

    def 'should write a dot file' () {
        given:
        def file = Files.createTempFile('nxf_','.dot')
        def gr = new GraphObserver(file)
        gr.dag = test_dag

        when:
        gr.onFlowComplete()

        then:
        def result = file.text
        // is dot
        result.contains("digraph ${file.baseName} {")
        // contains expected nodes
        result.contains('label="Source"')
        result.contains('label="Process 1"')
        result.contains('label="Filter"')
        result.contains('label="Process 2"')

        // contains at least one edge
        result.contains('->')

        cleanup:
        file.delete()
    }


    def 'should write an html file' () {
        given:
        def file = Files.createTempFile('nxf-','.html')
        def gr = new GraphObserver(file)
        gr.dag = test_dag

        when:
        gr.onFlowComplete()

        then:
        def result = file.text

        // is html
        result.contains('<html>')
        result.contains('</html>')

        // contains some of the expected json
        result.contains("label: 'Source'")
        result.contains("label: 'Process 1'")
        result.contains("label: 'Filter'")
        result.contains("label: 'Process 2'")

        cleanup:
        file.delete()
    }


    @Requires( { TestHelper.graphvizInstalled() } )
    def 'should write an svg file' () {
        given:
        def file = Files.createTempFile('nxf-','.svg')
        def gr = new GraphObserver(file)
        gr.dag = test_dag

        when:
        gr.onFlowComplete()

        then:
        // just assert that _something_ has been written
        file.text.contains('<svg')

        cleanup:
        file.delete()
    }

    @Requires( { TestHelper.graphvizInstalled() } )
    def 'should write a png file' () {
        given:
        def file = Files.createTempFile('nxf-','.png')
        def gr = new GraphObserver(file)
        gr.dag = test_dag

        when:
        gr.onFlowComplete()

        then: 'compare with PNG magic number'
        file.bytes[0..7] as byte[] == [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] as byte[]

        cleanup:
        file.delete()
    }

    @Requires( { TestHelper.graphvizInstalled() } )
    def 'should write a pdf file' () {
        given:
        def file = Files.createTempFile('nxf-','.pdf')
        def gr = new GraphObserver(file)
        gr.dag = test_dag

        when:
        gr.onFlowComplete()

        then: 'compare with PDF magic number'
        file.bytes[0..3] as byte[] == [0x25, 0x50, 0x44, 0x46] as byte[]

        cleanup:
        file.delete()
    }

    def 'should output a dot file when no extension is specified' () {
        given:
        def folder = Files.createTempDirectory('test')
        def file = folder.resolve('nope')
        def gr = new GraphObserver(file)
        gr.dag = test_dag

        when:
        gr.onFlowComplete()

        then:
        def result = file.text
        // is dot
        result.contains("digraph ${file.baseName} {")
        // contains expected nodes
        result.contains('label="Source"')
        result.contains('label="Process 1"')
        result.contains('label="Filter"')
        result.contains('label="Process 2"')
        // contains at least one edge
        result.contains('->')

        cleanup:
        folder.deleteDir()
    }

    def 'should return a renderer instance' () {

        given:
        def observer

        when:
        observer = new GraphObserver(Paths.get('/path/to/hello-world.dot'))
        then:
        observer.name == 'hello-world'
        observer.format == 'dot'
        observer.createRender() instanceof DotRenderer

        when:
        observer = new GraphObserver(Paths.get('/path/to/TheGraph.html'))
        then:
        observer.name == 'TheGraph'
        observer.format == 'html'
        observer.createRender() instanceof CytoscapeHtmlRenderer

        when:
        observer = new GraphObserver(Paths.get('/path/to/TheGraph.SVG'))
        then:
        observer.name == 'TheGraph'
        observer.format == 'svg'
        observer.createRender() instanceof GraphvizRenderer

        when:
        observer = new GraphObserver(Paths.get('/path/to/anonymous'))
        then:
        observer.name == 'anonymous'
        observer.format == 'dot'
        observer.createRender() instanceof DotRenderer
    }
}
