// JavaScript used to power the Nextflow Report Template output.

//
// Converter methods
//

/**
 * Converts a list of numbers in milli-units (mWh, mg,...) to base units (Wh, g)
 * @param {ArrayLike} list An array with numbers
 * @returns The array, with each value divided by 1000
 */
function norm_units( list ) {
  return list?.map(v => v / 1000) ?? null;
}

/**
 * Finds the index of a unit symbol in the given list
 *
 * @param {string} symbol The unit symbol to find
 * @param {string[]} list The list of valid unit symbols
 * @returns {number} Index of the symbol in the list
 * @throws {Error} If the symbol is not found
 */
function getIdx(symbol, list) {
    const idx = list.indexOf(symbol);
    if (idx === -1) throw new Error(`Invalid symbol: "${symbol}"`);
    return idx;
}

/**
 * Formats a numeric value and unit into a readable string
 * Strips unnecessary trailing zeros, except when it would result in an integer
 *
 * @param {[number, string]} tuple A tuple containing [value, unit]
 * @returns {string} Formatted value and unit
 */
function toReadable([value, unit], separator = ' ') {
    if (value == null) return '';
    const strValue = Number.isInteger(value) ? value : value.toString().replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1');
    return unit ? `${strValue}${separator}${unit}` : strValue;
}

/**
 * Scales a numeric value between units using fixed step factors
 *
 * @param {number} value The value to scale
 * @param {number} fromIndex Starting unit index
 * @param {number} toIndex Target unit index
 * @param {number[]} steps Array of step factors between consecutive units
 * @returns {number} The scaled value
 */
function scaleLinear(value, fromIndex, toIndex, steps) {
    if (toIndex > fromIndex) {
        for (const step of steps.slice(fromIndex, toIndex)) value /= step;
    } else if (toIndex < fromIndex) {
        for (const step of steps.slice(toIndex, fromIndex)) value *= step;
    }
    return value;
}

/**
 * Scales numeric values with units (e.g., B, kB, MB) using either a specified target scale
 * or automatic logarithmic scaling
 *
 * @param {number} value Numeric value to scale
 * @param {string} scale The current scale prefix (e.g., '', 'k', 'M')
 * @param {string} unit The unit suffix (e.g., 'B', 'Wh')
 * @param {string|null} targetScale Optional target scale prefix
 * @returns {[number, string]} Tuple of scaled value and scaled unit
 */
function scaleUnits(value, scale = '', unit = '', targetScale = null) {
    const scalingFactor = unit === 'B' ? 1024 : 1000;
    const scales = ['p', 'n', 'u', 'm', '', 'k', 'M', 'G', 'T', 'P', 'E'];

    const scaleIndex = getIdx(scale, scales);
    let targetScaleIndex, difference;

    if (targetScale != null) {
        targetScaleIndex = getIdx(targetScale, scales);
        difference = targetScaleIndex - scaleIndex;
    } else {
        difference = Math.floor(Math.log(value) / Math.log(scalingFactor));
        targetScaleIndex = scaleIndex + difference;
    }

    const newValue = value / Math.pow(scalingFactor, difference);
    const scaledUnit = (scales[targetScaleIndex] || '') + (unit || '');
    return [newValue, scaledUnit];
}

/**
 * Converts numeric values with units to a readable string
 *
 * @param {number} value Numeric value to scale
 * @param {string} scale Current scale prefix
 * @param {string} unit Unit suffix
 * @param {string|null} targetScale Optional target scale prefix
 * @param {number} precision Number of decimal places to keep
 * @returns {string} Readable string (e.g., "1.23 MB")
 */
function toReadableUnits(value, scale = '', unit = '', targetScale = null, precision = 2) {
    if (value == null) return '';
    let [newValue, scaledUnit] = scaleUnits(value, scale, unit, targetScale);
    newValue = Number(newValue.toFixed(precision));
    return toReadable([newValue, scaledUnit]);
}

/**
 * Converts the given time to another unit of time
 *
 * @param {number} value The time as a number in original given unit
 * @param {string} unit Given unit of time
 * @param {string} targetUnit Unit of time to be converted to
 * @returns {[number, string]} Number of converted time and its unit
 */
function scaleTime(value, unit = 'ms', targetUnit = 's') {
    const units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years'];
    const steps = [1000, 1000, 1000, 60, 60, 24, 7, 4.35, 12];

    const from = getIdx(unit, units);
    const to = getIdx(targetUnit, units);

    let newValue = scaleLinear(value, from, to, steps);

    if (newValue === 1 && ['days', 'weeks', 'months', 'years'].includes(targetUnit)) {
        targetUnit = targetUnit.slice(0, -1);
    }
    return [newValue, targetUnit];
}

/**
 * Converts a time value into a human-readable multi-unit string
 *
 * @param {number} value Numeric value of the time
 * @param {string} unit Current unit of time
 * @param {string} smallestUnit The smallest unit to include
 * @param {string} largestUnit The largest unit to include
 * @param {number} threshold Minimum value before a unit is displayed
 * @returns {string} Readable string (e.g., "1 days 2 h 3 min")
 */
function toReadableTimeUnits(value, unit = 'ms', smallestUnit = 's', largestUnit = 'years', threshold = 0.0) {
    let units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years'];
    units = units.slice(getIdx(smallestUnit, units), getIdx(largestUnit, units) + 1).reverse();

    const parts = [];
    for (const targetUnit of units) {
        let [numeric, tu] = scaleTime(value, unit, targetUnit);

        if (targetUnit === smallestUnit) {
            numeric = Number(numeric.toFixed(2));
            parts.push(toReadable([numeric, tu]));
        } else {
            numeric = Math.floor(numeric);
            if (threshold == null || numeric > threshold) {
                value -= scaleTime(numeric, targetUnit, unit)[0];
                parts.push(toReadable([numeric, tu], ''));
                if (value === 0) break;
            }
        }
    }
    return parts.join(' ');
}

//
// Render functions
// These functions are required for rendering in the table. In particular they ensure that:
// 1. Raw values are used, when necessary (e.g. for sorting)
// 2. Render the values together with units, when it should be human readable
//

/**
 * Perform checks on the data before using other methods to process
 *
 * @param {*} data The data (number/string/...)
 * @param {*} type Type of the data
 * @param {function} parseFunction Function to parse the data
 * @returns
 */
function toRawValue(data, type, parseFunction) {
  if (type === 'sort') {
    return parseFunction(data);
  }
  if($('#nf-table-humanreadable').val() == 'false'){
    return data;
  }
  if (data == '-' || data == 0){
    return data;
  }
  return null
}

/**
 * Render CO2 equivalents in desired output format
 *
 * @param {*} value CO2 equivalents in milli-grams
 * @param {*} type Type of the data
 * @returns CO2 equivalents as a readable unit
 */
function make_co2e(mg, type){
  return toRawValue(mg, type, parseFloat) ?? toReadableUnits(mg, 'm', 'g');
}

/**
 * Render energy in desired output format
 *
 * @param {*} mWh Energy in milli-Watt-hours
 * @param {*} type Type of the data
 * @returns Energy as a readable unit
 */
function make_energy(mWh, type){
  return toRawValue(mWh, type, parseFloat) ?? toReadableUnits(mWh, 'm', 'Wh');
}

/**
 * Render time in desired output format
 *
 * @param {*} h Time in hours
 * @param {*} type Type of the data
 * @returns The readable time
 */
function make_time(h, type){
  return toRawValue(h, type, parseFloat) ?? toReadableTimeUnits(parseFloat(h), 'h', 'ms', 'days', 0.0);
}

/**
 * Render carbon intensity in desired output format
 *
 * @param {*} ci Carbon intensity value
 * @param {*} type Type of the data
 * @returns Carbon intensity as a readable unit
 */
function make_carbon_intensity(ci, type) {
  return toRawValue(data, type, parseFloat) ?? toReadableUnits(ci, 'm', 'gCO<sub>2</sub>e/kWh');
}

/**
 * Render memory size in desired output format
 *
 * @param {*} value Memory value to be rendered
 * @param {*} type Type of the data
 * @param {string} unit The unit of the input value (default: 'B')
 * @returns The memory at a readable scale
 */
function make_memory(value, type) {
  return toRawValue(value, type, parseFloat) ?? toReadableByteUnits(value, 'GB');
}

/**
 * Render the per core power draw in desired output format
 *
 * @param {*} powerDrawCPU Power draw of the CPU in Watts
 * @param {*} type Type of the data
 * @returns Readable power draw in Watts
 */
function make_power_draw_cpu(value, type){
  return toRawValue(value, type, parseFloat) ?? toReadableUnits(value, '', 'W/Core');
}

/**
 * Render usage factor in desired output format
 *
 * @param {*} usageFactor Usage factor of CPU in percent
 * @param {*} type Type of the data
 * @returns Readable usage factor
 */
function make_core_usage_factor(usageFactor, type){
  return toRawValue(usageFactor, type, parseFloat) ?? usageFactor;
}

// Map for collecting statistics by process
window.statsByProcess = {};

//
// This block is only executed after the page is fully loaded
//
$(function() {
  // Script block clicked
  $('#tasks_table').on('click', '.script_block', function(e){
    e.preventDefault();
    $(this).toggleClass('short');
  });

  $(function() {
    $('[data-toggle="tooltip"]').tooltip()
  })

  // Completed date from now
  var completed_date = moment( $('#workflow_complete').text(), "ddd MMM DD HH:mm:ss .* YYYY" );
  if(completed_date.isValid()){
    $('#completed_fromnow').html('completed ' + completed_date.fromNow() + ', ');
  }

  // Collect metrics by process
  for(let processName in window.data.summary){
    let metrics = window.data.summary[processName];

    // Add an empty map if the process is not already present
    window.statsByProcess[processName] ??= {};

    for (let metricName in metrics) {
      // Skip if metric is not present
      if (metrics[metricName] == null) { continue; }

      if( metrics[metricName]['min'] == metrics[metricName]['max'] ) {
        // min equals max ==> show just a value
        window.statsByProcess[processName][metricName] = [ metrics[metricName]['min'] ];
      }
      else {
          // otherwise show all values
          window.statsByProcess[processName][metricName] = ['min', 'q1', 'q2', 'q3', 'max'].map(stat => metrics[metricName][stat])
      }
      if (metricName == "time") {
        window.statsByProcess[processName][metricName] = window.statsByProcess[processName][metricName].map(function(d,i){
          return moment.duration(d).asMinutes().toFixed(1);
        });
      }
    }
  }

  // Plot histograms of resource usage
  var plot_data_total = [];
  var plot_data_non_cached = [];
  for(var processName in window.statsByProcess){

    // Extract process statistics
    var stats = window.statsByProcess[processName];

    // Add CO2 Boxplot to plot
    plot_data_total.push(
      {
        x:processName, y: norm_units(stats.co2e), name: processName,
        type:'box', boxmean: true, boxpoints: false
      }
    );

    // Add energy to link to the right y-axis, hiding the object, hover info and legend itself
    plot_data_total.push(
      {
        x:processName, y: norm_units(stats.energy), name: processName,
        type:'box', boxmean: true, boxpoints: false, yaxis: 'y2', showlegend: false,
        hoverinfo: 'skip', marker: {color: 'rgba(0,0,0,0)'}, fillcolor: 'rgba(0,0,0,0)'
      }
    );

    // Add outline of CO2 emissions from non-cached processes to plot
    plot_data_non_cached.push(
      {
        x:processName, y: norm_units(stats.co2e_non_cached), name: processName,
        type:'box', boxmean: true, boxpoints: false,
      }
    );

    // Add energy to link to the right y-axis, hiding the object, hover info and legend itself
    plot_data_non_cached.push(
      {
        x:processName, y: norm_units(stats.energy_non_cached), name: processName,
        type:'box', boxmean: true, boxpoints: false, yaxis: 'y2', showlegend: false,
        hoverinfo: 'skip', marker: {color: 'rgba(0,0,0,0)'}, fillcolor: 'rgba(0,0,0,0)'
      }
    );
  }

  var layout = {
    title: 'CO<sub>2</sub> emission & energy consumption',
    legend: {
      x: 1.1
    },
    xaxis: {
      title: 'Processes',
    },
    yaxis: {
      title: 'CO2e emission (g)',
      rangemode: 'tozero',
    },
    yaxis2: {
      title: 'Energy consumption (Wh)',
      rangemode: 'tozero',
      gridcolor: 'rgba(0, 0, 0, 0)', // transparent grid lines
      overlaying: 'y',
      side: 'right',
    },
    boxmode: 'group',
  };

  Plotly.newPlot('co2e-total-plot', plot_data_total, layout);
  Plotly.newPlot('co2e-non-cached-plot', plot_data_non_cached, layout);

  //
  // Table creation functions
  //

  /**
   * Function to create the table of tasks
   */
  function make_tasks_table(){
    // reset
      if ( $.fn.dataTable.isDataTable( '#tasks_table' ) ) {
        $('#tasks_table').DataTable().destroy();
      }

      // Column titles
      var energyConsumptionTitle = 'energy consumption (mWh)'; // Default column title
      var co2EmissionsTitle = 'CO₂e emissions (mg)';
      if ($('#nf-table-humanreadable').val() == 'true') {
        energyConsumptionTitle = 'energy consumption'; // Change the column title if the button is selected
        co2EmissionsTitle = 'CO₂e emissions';
      }

      var table = $('#tasks_table').DataTable({
        data: window.data.trace,
        columns: [
          { title: 'task_id', data: 'task_id' },
          { title: 'process', data: 'process' },
          { title: 'tag', data: 'tag' },
          { title: 'status', data: 'status', render: function(data, type, row){
              var s = {
                COMPLETED: 'success',
                CACHED: 'secondary',
                ABORTED: 'danger',
                FAILED: 'danger'
              }
              return '<span class="badge badge-'+s[data]+'">'+data+'</span>';
            }
          },
          { title: 'hash', data: 'hash', render:  function(data, type, row){
              var script = '';
              var lines = data.split("\n");
              var ws_re = /^(\s+)/g;
              var flws_match = ws_re.exec(lines[1]);
              if(flws_match == null){
                script = data;
              } else {
                for(var j=0; j<lines.length; j++){
                  script += lines[j].replace(new RegExp('^'+flws_match[1]), '').replace(/\s+$/,'') + "\n";
                }
              }
              return '<code>'+script+'</code>';
            }
          },
          { title: energyConsumptionTitle, data: 'energy', type: 'num', render: make_energy },
          { title: co2EmissionsTitle, data: 'co2e', type: 'num', render: make_co2e },
          { title: `${co2EmissionsTitle} (market)`, data: 'co2eMarket', type: 'num', render: make_co2e },
          { title: 'carbon intensity', data: 'ci', type: 'num', render: make_carbon_intensity },
          { title: 'allocated cpus', data: 'cpus', type: 'num' },
          { title: '%cpu', data: 'cpuUsage', type: 'num', render: make_core_usage_factor },
          { title: 'allocated memory', data: 'memory', type: 'num', render: make_memory },
          { title: 'realtime', data: 'time', type: 'num', render: make_time },
          { title: 'power draw (in W/core)', data: 'powerdrawCPU', type: 'num', render: make_power_draw_cpu },
          { title: 'cpu model', data: 'cpu_model' },
        ],
        "deferRender": true,
        "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
        "scrollX": true,
        "colReorder": true,
        "columnDefs": [
          { className: "id", "targets": [ 0,1,2,3 ] },
          { className: "meta", "targets": [ 4,7,8,9,10,11 ] },
          { className: "metrics", "targets": [ 5,6 ] }
        ],
        "buttons": [
          {
            extend: 'colvisGroup',
            text: 'Metrics',
            show: [ '.id', '.metrics' ],
            hide: [ '.meta' ],
          },
          {
            extend: 'colvisGroup',
            text: 'Metadata',
            show: [ '.id', '.meta'],
            hide: [ '.metrics' ],
          },
          {
            extend: 'colvisGroup',
            text: 'All',
            show: ':hidden',
          },
        ]
      });

      // Insert column filter button group
      table.buttons().container()
        .prependTo( $('#tasks_table_filter') );

      // Column filter button group onClick event to highlight active filter
      $('.buttons-colvisGroup').click(function(){
        var def = 'btn-secondary';
        var sel = 'btn-primary';
        $('.buttons-colvisGroup').removeClass(sel).addClass(def);
        $(this).addClass(sel).removeClass(def);
      });

      // Default filter highlight
      $(".buttons-colvisGroup:contains('All')").click();
  }

  // Executor for task table creation (on page load)
  if( window.data.trace==null ) {
      // Hide tasks table if too many tasks are present
      $('#tasks-present-table').remove()
  }
  else {
      $('#tasks-omitted-table').remove()
      // Dropdown changed about raw / human readable values in table
      $('#nf-table-humanreadable').change(function(){
        make_tasks_table();
      });
      // Make the table on page load
      make_tasks_table();
  }

  /**
   * Function to create the table of options / configurations
   */
  function make_options_table(){
    // reset
    if ( $.fn.dataTable.isDataTable( '#options_table' ) ) {
      $('#options_table').DataTable().destroy();
    }

    var table = $('#options_table').DataTable({
      data: window.options,
      columns: [
          { title: "Option", data: "option" },
          { title: "Value", data: "value" }
        ],
        "deferRender": true,
        "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
    });
  }

  // Executor for options table creation (on page load)
  make_options_table();
});