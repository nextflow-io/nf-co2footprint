package nextflow.co2footprint.DataContainers

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Paths

class MachineTypeDataMatrixTests extends Specification {
    @Shared
    MachineTypeDataMatrix machineTypeDataMatrix = MachineTypeDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/machine_type/machine_type_test.csv').toURI())
    )

    def 'Match executor correctly'() {
        when:
        MachineTypeDataMatrix executorDataMatrix = machineTypeDataMatrix.matchExecutor(executor)

        then:
        if (executorDataMatrix) {
            executorDataMatrix.getMachineType() == expectedMachineType
            executorDataMatrix.getPUE() == expectedPUE
        }
        else {
            expectedPUE == null && expectedMachineType == null
        }

        where:
        executor        || expectedPUE  || expectedMachineType
        'awsbatch'      || 1.15         || 'cloud'
        'slurm'         || 1.67         || 'compute cluster'
        'X'             || null         || null
    }
}