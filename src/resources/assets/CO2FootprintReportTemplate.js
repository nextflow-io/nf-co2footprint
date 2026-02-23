// JavaScript used to power the Nextflow Report Template output.
/**
 * Decides whether raw or readable values are to be displayed
 *
 * @param {*} data The data
 * @param {*} type Type of the data
 * @returns
 */
function rawOrReadable(data, type) {
  if (type === 'sort' || $('#nf-table-humanreadable').val() == 'false') {
    return data['raw'].value
  }
  if (data.hasOwnProperty('report')) {
    return data['report']
  }
  return data['readable']
}

// Map for collecting statistics by process
window.statsByProcess = {};

//
// MAIN BLOCK: This block is only executed after the page is fully loaded
//
$(function () {
  // Script block clicked
  $('#tasks_table').on('click', '.script_block', function (e) {
    e.preventDefault()
    $(this).toggleClass('short')
  })

  $(function () {
    $('[data-toggle="tooltip"]').tooltip()
  })

  // Completed date from now
  var completed_date = moment($('#workflow_complete').text(), "ddd MMM DD HH:mm:ss .* YYYY")
  if (completed_date.isValid()) {
    $('#completed_fromnow').html('completed ' + completed_date.fromNow() + ', ')
  }

  // Plot histograms of resource usage
  function plot_resource_usage() {
    var plot_data = { total: [], total_non_cached: [] };
    for (var process in window.data.summary) {

      // Extract process statistics
      var stats = window.data.summary[process]
      var processName = process.split(':').pop() ?? process

      // Put stats in plot
      for (const [i, suffix] of ["", "_non_cached"].entries()) {
        // Add CO₂ Boxplot to plot
        plot_data[`total${suffix}`].push(
          {
            x: process, y: stats[`co2e${suffix}`], name: processName,
            type: 'box', boxmean: true, boxpoints: 'all', hoverinfo: "y"
          }
        )
        // Add energy to link to the right y-axis, hiding the object, hover info and legend itself
        plot_data[`total${suffix}`].push(
          {
            x: process, y: stats[`energy${suffix}`]?.map(v => v * 1000) ?? null, name: processName,
            type: 'box', boxmean: true, boxpoints: false, yaxis: 'y2', showlegend: false,
            hoverinfo: 'skip', marker: { color: 'rgba(0,0,0,0)' }, fillcolor: 'rgba(0,0,0,0)'
          }
        )
      }
    }

    var layout = {
      title: { text: 'CO<sub>2</sub> emission & energy consumption' },
      legend: {
        x: 1.1
      },
      xaxis: {
        title: { text: 'Processes' },
      },
      yaxis: {
        title: { text: 'CO₂e emission (g)' },
        rangemode: 'tozero',
      },
      yaxis2: {
        title: { text: 'Energy consumption (Wh)' },
        rangemode: 'tozero',
        gridcolor: 'rgba(0, 0, 0, 0)', // transparent grid lines
        overlaying: 'y',
        side: 'right',
      }
    }

    Plotly.newPlot("co2e-total-plot", plot_data.total, layout);
    Plotly.newPlot("co2e-non-cached-plot", plot_data.total_non_cached, layout);
  }

  plot_resource_usage()

  //
  // Table creation functions
  //

  /**
   * Function to create the table of tasks
   */
  function make_tasks_table() {
    // reset
    if ($.fn.dataTable.isDataTable('#tasks_table')) {
      $('#tasks_table').DataTable().destroy()
    }

    // Column titles
    var energyConsumptionProcessorTitle = 'energy consumption (processor) [mWh]'
    var energyConsumptionMemoryTitle = 'energy consumption (memory) [mWh]'
    var energyConsumptionTitle = 'energy consumption (incl. PUE) [mWh]' // Default column title
    var co2EmissionsTitle = 'CO₂e emissions [mg]'
    if ($('#nf-table-humanreadable').val() == 'true') {
      energyConsumptionProcessorTitle = 'energy consumption (processor)'
      energyConsumptionMemoryTitle = 'energy consumption (memory)'
      energyConsumptionTitle = 'energy consumption (incl. PUE)' // Change the column title if the button is selected
      co2EmissionsTitle = 'CO₂e emissions'
    }

    var table = $('#tasks_table').DataTable({
      data: window.data.trace,
      columns: [
        { title: 'task_id', data: 'task_id' },
        { title: 'process', data: 'process' },
        { title: 'tag', data: 'tag' },
        { title: 'status', data: 'status' },
        { title: 'hash', data: 'hash' },
        { title: energyConsumptionTitle, data: 'energy' },
        { title: energyConsumptionProcessorTitle, data: 'rawEnergyProcessor' },
        { title: energyConsumptionMemoryTitle, data: 'rawEnergyMemory' },
        { title: co2EmissionsTitle, data: 'co2e' },
        { title: `${co2EmissionsTitle} (market)`, data: 'co2eMarket' },
        { title: 'carbon intensity', data: 'ci' },
        { title: 'allocated cpus', data: 'cpus' },
        { title: '%cpu', data: 'cpuUsage' },
        { title: 'allocated memory', data: 'memory' },
        { title: 'realtime', data: 'time' },
        { title: 'power draw (in W/core)', data: 'powerdrawCPU' },
        { title: 'cpu model', data: 'cpu_model' },
      ],
      deferRender: true,
      lengthMenu: [[25, 50, 100, -1], [25, 50, 100, "All"]],
      scrollX: true,
      colReorder: true,
      columnDefs: [
        { targets: '_all', render: rawOrReadable },
        { className: "id", "targets": [0, 1, 2, 3] },
        { className: "meta", "targets": [4, 7, 8, 9, 10, 11] },
        { className: "metrics", "targets": [5, 6] }
      ],
      dom:
        "<'row'<'col-auto'l><'col text-center'B><'col-auto'f>>" +
        "<'row'<'col-12'tr>>" +
        "<'row'<'col'i><'col-auto'p>>",
      buttons: [
        {
          extend: 'colvisGroup',
          text: 'Metrics',
          show: ['.id', '.metrics'],
          hide: ['.meta'],
        },
        {
          extend: 'colvisGroup',
          text: 'Metadata',
          show: ['.id', '.meta'],
          hide: ['.metrics'],
        },
        {
          extend: 'colvisGroup',
          text: 'All',
          show: ':hidden',
        },
      ]
    })

    // Insert column filter button group
    table.buttons().container().prependTo($('#tasks_table_filter'))

    // Column filter button group onClick event to highlight active filter
    $('.buttons-colvisGroup').click(function () {
      var def = 'btn-secondary'
      var sel = 'btn-primary'
      $('.buttons-colvisGroup').removeClass(sel).addClass(def)
      $(this).addClass(sel).removeClass(def)
    })

    // Default filter highlight
    $(".buttons-colvisGroup:contains('All')").click()
  }

  // Executor for task table creation (on page load)
  if (window.data.trace == null) {
    // Hide tasks table if too many tasks are present
    $('#tasks-present-table').remove()
  }
  else {
    $('#tasks-omitted-table').remove()
    // Dropdown changed about raw / human readable values in table
    $('#nf-table-humanreadable').change(function () {
      make_tasks_table()
    })
    // Make the table on page load
    make_tasks_table()
  }

  /**
   * Function to create the table of options / configurations
   */
  function make_options_table() {
    // reset
    if ($.fn.dataTable.isDataTable('#options_table')) {
      $('#options_table').DataTable().destroy()
    }

    var table = $('#options_table').DataTable({
      data: window.options,
      columns: [
        { title: "Option", data: "option" },
        { title: "Value", data: "value" }
      ],
      "deferRender": true,
      "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
    })
  }

  // Executor for options table creation (on page load)
  make_options_table()

  //
  // Carbon intensity plot
  //

  function make_ci_plot() {
    var ci_plot_data = []

    // Add Date() entry to start and end time of tasks
    window.data.trace.forEach(task => {
      task.start.time = new Date(task.start.raw.value)
      task.complete.time = new Date(task.complete.raw.value)
    })

    // Tasks:
    // Collect all time boundaries
    const timePoints = new Set()
    for (const task of window.data.trace) {
      timePoints.add(task.start.time)
      timePoints.add(task.complete.time)
    }
    const sortedTimes = Array.from(timePoints).sort((a, b) => a - b)

    // Define first and last point
    var [tasksStart, tasksEnd] = [sortedTimes[0], sortedTimes[sortedTimes.length - 1]]

    // For each subinterval, calculate the total energy from active intervals
    // Add first point to energy trace
    const timeSteps = [tasksStart], totalEnergy = [0.0]
    // Add intermediate points to energy trace
    for (let i = 0; i < sortedTimes.length - 1; i++) {
      const t0 = sortedTimes[i]
      const t1 = sortedTimes[i + 1]

      // Sum energy of intervals active in [t0, t1)
      let total = 0
      for (const task of window.data.trace) {
        if (task.start.time <= t0 && task.complete.time >= t1) {
          total += task.energy.raw.value
        }
      }

      timeSteps.push(t0, t1)
      totalEnergy.push(total, total)
    }
    // Add last point to energy trace
    timeSteps.push(tasksEnd)
    totalEnergy.push(0.0)

    // CI Records:
    var ciRecords = new Map()

    // Change keys to Date() and sort
    for (let [key, value] of Object.entries(window.ciRecords)) {
      ciRecords.set(new Date(key), value)
    }
    ciRecords = new Map([...ciRecords.entries()].sort((a, b) => a[0] - b[0]))

    if (ciRecords.size == 0) {
      ciRecords.set(tasksStart, window.data.trace[0].ci.raw.value)
      ciRecords.set(tasksEnd, window.data.trace[0].ci.raw.value)
    }
    else {
      const times = [...ciRecords.keys()]
      const cis = [...ciRecords.values()]
      if (tasksStart < times[0]) {
        ciRecords.set(tasksStart, cis[0])
      }
      if (tasksEnd > times[times.length - 1]) {
        ciRecords.set(tasksEnd, cis[cis.length - 1])
      }
    }

    ciRecords = new Map([...ciRecords.entries()].sort((a, b) => a[0] - b[0]))
    // Step plot for carbon intensity
    var previousValue = null
    var timestamps = []
    var ciValues = []
    for (const [timestamp, value] of ciRecords) {
      if (previousValue != null) {
        timestamps.push(timestamp)
        ciValues.push(previousValue)
      }
      timestamps.push(timestamp)
      ciValues.push(value)
      previousValue = value
    }

    // Add CI trace to plot
    ci_plot_data.push(
      {
        name: "Carbon intensity",
        x: timestamps, y: ciValues,
        type: "scatter",
        mode: "lines+markers",
        line: { color: "grey" },
        marker: { color: "grey" },
        line: { shape: "hv", width: 2, color: "grey" },
        // Clean hover with units; x is formatted by hoverformat in layout
        hovertemplate:
          "CI: %{y:.1f} g/kWh<extra></extra>",
      }
    )

    // Add energy trace to plot
    ci_plot_data.push(
      {
        name: "Energy consumption",
        x: timeSteps, y: totalEnergy,
        type: "scatter",
        fill: "tozeroy", yaxis: "y2",
        line: { shape: "hv", width: 2, color: "#FFCC00" },
        hovertemplate:
          "Energy: %{y:.4f} kWh<extra></extra>",
      }
    )

    // Layout:
    var ci_layout = {
      title: { text: "Carbon intensity & energy over time" },
      margin: { l: 70, r: 70, t: 60, b: 60 },
      legend: {
        x: 1.05,
        y: 1,
        xanchor: "left",
        yanchor: "top",
      },
      xaxis: {
        title: { text: "Time" },
        range: [tasksStart, tasksEnd],
        ticklabelstandoff: 10,
        // Nice for long workflows:
        rangeslider: { visible: true, thickness: 0.08 },
        // Controls timestamp formatting in hover:
        hoverformat: "%Y-%m-%d %H:%M",
      },
      yaxis: {
        title: { text: "Carbon intensity (g/kWh)" },
        rangemode: "tozero",
        zeroline: true,
      },
      yaxis2: {
        title: { text: "Energy (kWh)" },
        rangemode: "tozero",
        overlaying: "y",
        side: "right",
        showgrid: false,
        zeroline: false,
      },
      // Cleaner hover box:
      hovermode: "x unified",
      hoverlabel: { align: "left" },
    }

    // Create plot:
    Plotly.newPlot("ci-plot", ci_plot_data, ci_layout, { responsive: true })
  }

  // Executor for ci plot generation
  make_ci_plot()
})