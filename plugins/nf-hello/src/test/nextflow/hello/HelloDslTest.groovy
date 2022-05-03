package nextflow.hello

import nextflow.Channel
import nextflow.extension.ChannelExtensionDelegate
import nextflow.plugin.Plugins
import spock.lang.Specification


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class HelloDslTest extends Specification{

    def setup () {
        ChannelExtensionDelegate.reloadExtensionPoints()
    }

    def 'should perform a hi and create a channel' () {
        when:
        def SCRIPT = '''
            channel.hello.sayHello() 
            '''
        and:
        def result = new MockScriptRunner([:]).setScript(SCRIPT).execute()
        then:
        result.val == 'Hi'
        result.val == Channel.STOP
    }

    def 'should store a goodbye' () {
        when:
        def SCRIPT = '''
            channel
                .of('Bye bye folks')
                .goodbye() 
            '''
        and:
        def result = new MockScriptRunner([:]).setScript(SCRIPT).execute()
        then:
        result.val == 'Bye bye folks'
        result.val == Channel.STOP

        and:
        HelloExtension.goodbyeMessage == 'Bye bye folks'
    }
}
