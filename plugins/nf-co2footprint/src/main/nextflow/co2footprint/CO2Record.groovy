package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CO2Records {

    //protected float co2e
    Map<String,Float> co2eRecords = ['init': (Float)0.0]
    // final? or something? to make sure for key value can be set only once?

//    // TODO would that be OK to do? necessary to access values from closure!
//    @Override
//    void setProperty(String property, Object newValue) {
//        getMetaClass().setProperty(this, property, newValue)
//    }

//    @Override
//    public Object getProperty(String property) {
//        return getMetaClass().getProperty(this, property);
//    }

}
