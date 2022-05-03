package nextflow.hello

import groovyx.gpars.dataflow.DataflowQueue
import nextflow.Channel
import nextflow.Session
import nextflow.extension.ChannelExtensionDelegate
import spock.lang.Specification


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class ChannelExtensionHelloTest extends Specification{

    def "should create a channel from hello"(){

        given:
        def session = Mock(Session)

        and:
        def helloExtension = new HelloExtension(); helloExtension.init(session)

        when:
        def result = helloExtension.sayHello()

        then:
        result.val == 'Hi'
        result.val == Channel.STOP
    }

    def "should receive a hi from hello"(){

        given:
        def session = Mock(Session)

        and:
        def helloExtension = new HelloExtension(); helloExtension.init(session)

        and:
        def ch = new DataflowQueue()
        ch.bind('Goodbye folks')
        ch.bind( Channel.STOP )

        when:
        def result = helloExtension.goodbye(ch)

        then:
        result.val == 'Goodbye folks'
        result.val == Channel.STOP
        helloExtension.goodbyeMessage == 'Goodbye folks'
    }
}
