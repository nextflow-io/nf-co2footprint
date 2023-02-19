package nextflow.hello

import nextflow.Channel
import nextflow.plugin.Plugins
import nextflow.plugin.TestPluginDescriptorFinder
import nextflow.plugin.TestPluginManager
import nextflow.plugin.extension.PluginExtensionProvider
import org.pf4j.PluginDescriptorFinder
import spock.lang.Shared
import spock.lang.Timeout
import test.Dsl2Spec

import java.nio.file.Path


/**
 * Unit test for Hello DSL
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 */
@Timeout(10)
class HelloDslTest extends Dsl2Spec{

    @Shared String pluginsMode

    def setup() {
        // reset previous instances
        PluginExtensionProvider.reset()
        // this need to be set *before* the plugin manager class is created
        pluginsMode = System.getProperty('pf4j.mode')
        System.setProperty('pf4j.mode', 'dev')
        // the plugin root should
        def root = Path.of('.').toAbsolutePath().normalize()
        def manager = new TestPluginManager(root){
            @Override
            protected PluginDescriptorFinder createPluginDescriptorFinder() {
                return new TestPluginDescriptorFinder(){
                    @Override
                    protected Path getManifestPath(Path pluginPath) {
                        return pluginPath.resolve('build/resources/main/META-INF/MANIFEST.MF')
                    }
                }
            }
        }
        Plugins.init(root, 'dev', manager)
    }

    def cleanup() {
        Plugins.stop()
        PluginExtensionProvider.reset()
        pluginsMode ? System.setProperty('pf4j.mode',pluginsMode) : System.clearProperty('pf4j.mode')
    }

    def 'should perform a hi and create a channel' () {
        when:
        def SCRIPT = '''
            include {reverse} from 'plugin/nf-hello'
            channel.reverse('hi!') 
            '''
        and:
        def result = new MockScriptRunner([hello:[prefix:'>>']]).setScript(SCRIPT).execute()
        then:
        result.val == 'hi!'.reverse()
        result.val == Channel.STOP
    }

    def 'should store a goodbye' () {
        when:
        def SCRIPT = '''
            include {goodbye} from 'plugin/nf-hello'
            channel
                .of('folks')
                .goodbye() 
            '''
        and:
        def result = new MockScriptRunner([:]).setScript(SCRIPT).execute()
        then:
        result.val == 'Goodbye folks'
        result.val == Channel.STOP
        
    }

    def 'can use an imported function' () {
        when:
        def SCRIPT = '''
            include {randomString} from 'plugin/nf-hello'
            channel
                .of( randomString(20) )                
            '''
        and:
        def result = new MockScriptRunner([:]).setScript(SCRIPT).execute()
        then:
        result.val.size() == 20
        result.val == Channel.STOP
    }

}
