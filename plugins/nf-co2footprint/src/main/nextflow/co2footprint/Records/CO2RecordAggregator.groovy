package nextflow.co2footprint.Records

import java.time.Duration
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.co2footprint.ResultsTree.RecordTree

// TODO: Make accumulation from Tree
/**
 * Compute summary statistics of the accumulated CO2Records of several tasks
 */
@Slf4j
@CompileStatic
class CO2RecordAggregator {
    // Collect metrics by process
    static List<Object> collectValsChildren(RecordTree node, String key) {
        List<Object> values = []

        // Catch special suffixes
        String suffix = '_' + key.split('_').drop(1).join('_')
        String valueKey = key
        if (suffix == '_non_cached') {
            valueKey = key.replace(suffix, '')
        }

        // Extract information
        if (!node.children && node?.value?.store?.containsKey(valueKey)) {
            Object value = node.value.store[valueKey]
            // Convert time to rounded minutes
            if (valueKey == 'time') {
                values.add(
                        Duration.ofMillis(value as Integer)
                                .toMinutes().toBigDecimal()
                                .setScale(1).stripTrailingZeros().toPlainString()
                )
            }
            // Extract non_cached values if requested
            else if (suffix == '_non_cached' && (node.value.store?.status == 'CACHED')) {
                // do nothing
            }
            else {
                values.add(value)
            }

        }
        else {
            node.children.forEach{ RecordTree child -> values += collectValsChildren(child, key) }
        }

        return values
    }

    static Set<String> collectKeysChildren(RecordTree node) {
        Set<String> keys = []
        if(node?.children) {
            node.children.each {
                RecordTree child -> keys = keys + collectKeysChildren(child)
            }
        } else {
            return node.value.store.keySet()
        }
        return keys
    }

    static Map<String, Map<String, Object>> collectByLevel(RecordTree node, String level, List<String> valueKeys=null){
        valueKeys ?= collectKeysChildren(node).toList()

        if (node.attributes?.level === level) {
            Map<String, Object> values = [:]
            valueKeys.forEach { String valueKey ->
                List<Object> vals = collectValsChildren(node, valueKey)
                if (vals) { values[valueKey] = vals }
            }

            return values ? [(node.name as String): values] as Map : [:]
        }

        // Recursion into deeper levels without collecting values
        Map<String, Map<String, Object>> levelValues = [:]
        node.children?.forEach{ RecordTree child ->
            levelValues = levelValues + collectByLevel(child, level, valueKeys)
        }

        return levelValues
    }
}
