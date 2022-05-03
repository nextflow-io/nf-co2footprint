package nextflow.hello

import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.DataflowWriteChannel
import groovyx.gpars.dataflow.expression.DataflowExpression
import nextflow.Channel
import nextflow.Global
import nextflow.Session
import nextflow.extension.ChannelExtensionPoint
import nextflow.extension.CH
import nextflow.NF
import nextflow.extension.DataflowHelper
import nextflow.plugin.Scoped

import java.util.concurrent.CompletableFuture

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Scoped('hello')
class HelloExtension extends ChannelExtensionPoint{

    private Session session

    @Override
    protected void init(Session session) {
        this.session = session
    }

    DataflowWriteChannel sayHello() {
        createHelloChannel()
    }

    static String goodbyeMessage

    DataflowWriteChannel goodbye(DataflowReadChannel source) {
        final target = CH.createBy(source)
        final next = {
            goodbyeMessage = "$it"
            target.bind(it)
        }
        final done = {
            target.bind(Channel.STOP)
        }
        DataflowHelper.subscribeImpl(source, [onNext: next, onComplete: done])
        target
    }

    protected DataflowWriteChannel createHelloChannel(){
        final channel = CH.create()
        if( NF.isDsl2() ){
            session.addIgniter { ->
                sayHelloImpl(channel)
            }
        }else{
            sayHelloImpl(channel)
        }
        channel
    }

    protected sayHelloImpl(final DataflowWriteChannel channel){
        def future = CompletableFuture.runAsync({
            channel.bind("Hi")
            channel.bind(Channel.STOP)
        })
        future.exceptionally(this.&handlerException)
    }

    static private void handlerException(Throwable e) {
        final error = e.cause ?: e
        log.error(error.message, error)
        final session = Global.session as Session
        session?.abort(error)
    }
}
