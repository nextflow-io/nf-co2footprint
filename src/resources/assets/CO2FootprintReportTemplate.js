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

  // Plot per-process emissions (CO₂e or energy, distribution or composition)
  function plot_resource_usage() {

    // Build minimum-unique-suffix display labels for all process keys.
    const allProcessKeys = Object.keys(window.data.summary)
    const summaryDisplayName = new Map()
    for (const key of allProcessKeys) {
      const parts = key.split(':')
      let depth = 1
      let label = parts[parts.length - 1]
      while (
        depth < parts.length &&
        allProcessKeys.filter(k => k === label || k.endsWith(':' + label)).length > 1
      ) {
        depth++
        label = parts.slice(parts.length - depth).join(':')
      }
      summaryDisplayName.set(key, label)
    }

    function arrMedian(arr) {
      if (!arr || arr.length === 0) return 0
      const s = [...arr].sort((a, b) => a - b)
      const m = Math.floor(s.length / 2)
      return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2
    }

    const commonHoverlabel = {
      bgcolor: 'rgba(28, 48, 66, 0.92)',
      bordercolor: 'rgba(28, 48, 66, 0.0)',
      font: { size: 12, color: '#FFFFFF' },
      align: 'left',
    }
    const commonBg = { plot_bgcolor: '#FCFEFF', paper_bgcolor: '#FFFFFF' }

    const state = { metric: 'co2e', cached: 'all', sorted: false }

    function colorFromName(name, lightness, alpha) {
      let hash = 0
      for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash)
      }
      const hue = Math.abs(hash) % 360
      return `hsla(${hue},62%,${lightness}%,${alpha})`
    }

    // Custom tooltip element — bypasses Plotly's rotated hover label for horizontal boxes.
    const tipEl = document.createElement('div')
    tipEl.style.cssText = [
      'position:fixed', 'display:none',
      'background:rgba(28,48,66,0.92)', 'color:#fff',
      'font-size:12px', 'padding:6px 10px',
      'border-radius:0', 'pointer-events:none',
      'z-index:9999', 'line-height:1.6', 'white-space:nowrap',
    ].join(';')
    document.body.appendChild(tipEl)
    document.addEventListener('mousemove', e => {
      if (tipEl.style.display !== 'none') {
        tipEl.style.left = (e.clientX + 14) + 'px'
        tipEl.style.top  = (e.clientY - 14) + 'px'
      }
    })

    function render() {
      const suffix = state.cached === 'all' ? '' : '_non_cached'
      const isEnergy = state.metric === 'energy'
      const unit = isEnergy ? 'Wh' : 'g CO\u2082e'
      const axisTitle = isEnergy ? 'Energy consumption (Wh)' : 'CO\u2082e emissions (g)'

      // Sort ascending (smallest \u2192 bottom, largest \u2192 top in horizontal chart).
      const keys = state.sorted
        ? [...allProcessKeys].sort((a, b) => {
            const aVals = window.data.summary[a][`${state.metric}${suffix}`] ?? []
            const bVals = window.data.summary[b][`${state.metric}${suffix}`] ?? []
            return arrMedian(aVals) - arrMedian(bVals)
          })
        : [...allProcessKeys]

      const displayNames = keys.map(k => summaryDisplayName.get(k) ?? k)
      const maxLabelLen = Math.max(...displayNames.map(l => l.length), 4)
      const leftMargin = Math.max(80, maxLabelLen * 7 + 12)

      const traces = keys.map((key, i) => {
        const vals = (window.data.summary[key][`${state.metric}${suffix}`] ?? [])
          .map(v => isEnergy ? v * 1000 : v)
        const name = displayNames[i]
        return {
          type: 'box',
          orientation: 'h',
          name,
          x: vals,
          y: Array(vals.length).fill(name),
          boxmean: true,
          boxpoints: 'all',
          jitter: 0.4,
          pointpos: 0,
          marker: { size: 5, color: colorFromName(name, 46, 0.75) },
          line: { color: colorFromName(name, 38, 1.0) },
          fillcolor: colorFromName(name, 70, 0.30),
          hoveron: 'boxes',
          hoverinfo: 'none',
        }
      })

      Plotly.react('process-emissions-plot', traces, {
        ...commonBg,
        showlegend: false,
        hovermode: 'closest',
        xaxis: { title: { text: axisTitle }, rangemode: 'tozero' },
        yaxis: {
          automargin: true,
          ...(state.sorted ? { categoryorder: 'array', categoryarray: displayNames } : {}),
        },
        margin: { l: leftMargin, r: 40, t: 20, b: 60 },
      }).then(() => {
        const plotDiv = document.getElementById('process-emissions-plot')
        plotDiv.removeAllListeners('plotly_hover')
        plotDiv.removeAllListeners('plotly_unhover')

        plotDiv.on('plotly_hover', evt => {
          const pt = evt.points[0]
          if (!pt) return
          const vals = [...pt.data.x].sort((a, b) => a - b)
          const n = vals.length
          const med = n % 2 ? vals[Math.floor(n / 2)] : (vals[n / 2 - 1] + vals[n / 2]) / 2
          const mean = pt.data.x.reduce((a, b) => a + b, 0) / n
          const q1 = vals[Math.floor(n * 0.25)]
          const q3 = vals[Math.min(Math.floor(n * 0.75), n - 1)]
          const fmt = v => String(Number(v.toPrecision(4)))
          tipEl.innerHTML = [
            `<b>${pt.data.name}</b>`,
            `Median: ${fmt(med)} ${unit}`,
            `Mean: ${fmt(mean)} ${unit}`,
            `Q1\u2013Q3: ${fmt(q1)}\u2013${fmt(q3)} ${unit}`,
          ].join('<br>')
          tipEl.style.display = 'block'
        })

        plotDiv.on('plotly_unhover', () => { tipEl.style.display = 'none' })
      })
    }

    function setActive(cls, activeId) {
      document.querySelectorAll('.' + cls).forEach(el => {
        el.classList.toggle('btn-primary', el.id === activeId)
        el.classList.toggle('btn-secondary', el.id !== activeId)
      })
    }

    document.getElementById('pe-btn-co2e').addEventListener('click', () => { state.metric = 'co2e'; setActive('pe-metric-btn', 'pe-btn-co2e'); render() })
    document.getElementById('pe-btn-energy').addEventListener('click', () => { state.metric = 'energy'; setActive('pe-metric-btn', 'pe-btn-energy'); render() })
    document.getElementById('pe-btn-all').addEventListener('click', () => { state.cached = 'all'; setActive('pe-cached-btn', 'pe-btn-all'); render() })
    document.getElementById('pe-btn-noncached').addEventListener('click', () => { state.cached = 'non_cached'; setActive('pe-cached-btn', 'pe-btn-noncached'); render() })
    document.getElementById('pe-btn-sort').addEventListener('click', () => {
      state.sorted = !state.sorted
      const btn = document.getElementById('pe-btn-sort')
      btn.classList.toggle('btn-primary', state.sorted)
      btn.classList.toggle('btn-secondary', !state.sorted)
      render()
    })

    render()
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

    const ciColor = '#0D6A8A'
    const energyColor = '#F4A21F'

    // Add CI trace to plot
    ci_plot_data.push(
      {
        name: "Carbon intensity",
        x: timestamps, y: ciValues,
        type: "scatter",
        mode: "lines",
        line: { shape: "hv", width: 3, color: ciColor },
        // Clean hover with units; x is formatted by hoverformat in layout
        hovertemplate: "<b>Carbon intensity</b><br>%{y:.1f} g/kWh<extra></extra>",
      }
    )

    // Add energy trace to plot
    ci_plot_data.push(
      {
        name: "Energy consumption",
        x: timeSteps, y: totalEnergy,
        type: "scatter",
        fill: "tozeroy", yaxis: "y2",
        fillcolor: 'rgba(244, 162, 31, 0.24)',
        line: { shape: "hv", width: 1.8, color: energyColor },
        hovertemplate: "<b>Energy</b><br>%{y:.4f} kWh<extra></extra>",
      }
    )

    // Layout:
    var ci_layout = {
      title: { text: "Carbon intensity & energy over time" },
      margin: { l: 140, r: 100, t: 40, b: 60 },
      plot_bgcolor: '#FCFEFF',
      paper_bgcolor: '#FFFFFF',
      legend: {
        x: 0.99,
        y: 0.99,
        xanchor: "right",
        yanchor: "top",
        bgcolor: 'rgba(255,255,255,0.80)',
        bordercolor: 'rgba(36,64,87,0.20)',
        borderwidth: 1,
      },
      xaxis: {
        title: { text: "Time", font: { color: '#000000' } },
        range: [tasksStart, tasksEnd],
        ticklabelstandoff: 10,
        showgrid: true,
        gridcolor: 'rgba(36,64,87,0.08)',
        // Nice for long workflows:
        rangeslider: {
          visible: false,
          thickness: 0.055,
          bgcolor: 'rgba(36,64,87,0.06)',
          bordercolor: 'rgba(36,64,87,0.15)',
        },
        // Controls timestamp formatting in hover:
        hoverformat: "%Y-%m-%d %H:%M",
      },
      yaxis: {
        title: { text: "Carbon intensity (g/kWh)", font: { color: '#000000' } },
        rangemode: "tozero",
        zeroline: true,
        gridcolor: 'hsla(195, 83%, 30%, 0.12)',
      },
      yaxis2: {
        title: { text: "Energy (kWh)", font: { color: '#000000' } },
        rangemode: "tozero",
        overlaying: "y",
        side: "right",
        automargin: true,
        showgrid: false,
        zeroline: false,
      },
      // Cleaner hover box:
      hovermode: "x unified",
      hoverlabel: {
        bgcolor: 'rgba(28, 48, 66, 0.92)',
        bordercolor: 'rgba(28, 48, 66, 0.0)',
        font: { size: 12, color: '#FFFFFF' },
        align: 'left',
      },
    }

    // Create plot:
    return Plotly.newPlot("ci-plot", ci_plot_data, ci_layout, { responsive: true })
  }

  function ensure_process_swimlane_container() {
    var existingPlot = document.getElementById('process-swimlane-plot')
    if (existingPlot) {
      return existingPlot
    }

    var ciPlot = document.getElementById('ci-plot')
    if (!ciPlot) {
      return null
    }

    var sectionContainer = ciPlot.closest('.container') || ciPlot.parentElement
    if (!sectionContainer) {
      return null
    }

    var heading = document.createElement('h3')
    heading.id = 'process-swimlanes'
    heading.className = 'mt-4'
    heading.textContent = 'Process task timeline'

    var plotDiv = document.createElement('div')
    plotDiv.id = 'process-swimlane-plot'

    var description = document.createElement('p')
    description.className = 'mt-3 mb-3'
    description.textContent = 'This Gantt-style swimlane plot shows task runtime windows grouped by process. Each horizontal bar corresponds to one task execution interval.'

    sectionContainer.appendChild(heading)
    sectionContainer.appendChild(plotDiv)
    sectionContainer.appendChild(description)

    return plotDiv
  }

  function make_process_swimlane_plot() {
    if (!window.data.trace || window.data.trace.length === 0) {
      return null
    }

    var swimlanePlotContainer = ensure_process_swimlane_container()
    if (!swimlanePlotContainer) {
      return null
    }

    function normalizeTaskField(value) {
      if (value == null) {
        return ''
      }
      if (typeof value === 'string') {
        return value
      }
      if (typeof value === 'number' || typeof value === 'boolean') {
        return String(value)
      }
      if (typeof value === 'object') {
        if (value.report != null) {
          return String(value.report)
        }
        if (value.readable != null) {
          return String(value.readable)
        }
        if (value.raw != null) {
          if (typeof value.raw === 'object' && value.raw.value != null) {
            return String(value.raw.value)
          }
          return String(value.raw)
        }
      }
      return String(value)
    }

    const tasksByProcess = new Map()
    let timelineStart = null
    let timelineEnd = null
    for (const task of window.data.trace) {
      // Use the full process path as the grouping key to avoid collisions
      // between processes that share only their last segment (e.g. A:B:FASTQ vs C:D:FASTQ).
      const processKey = normalizeTaskField(task.process) || 'unknown'
      const start = new Date(task.start?.raw?.value)
      const complete = new Date(task.complete?.raw?.value)

      if (Number.isNaN(start.getTime()) || Number.isNaN(complete.getTime()) || complete < start) {
        continue
      }

      if (!tasksByProcess.has(processKey)) {
        tasksByProcess.set(processKey, [])
      }

      tasksByProcess.get(processKey).push({
        task_id: normalizeTaskField(task.task_id) || 'n/a',
        status: normalizeTaskField(task.status) || 'n/a',
        start: start,
        complete: complete,
      })

      if (timelineStart == null || start < timelineStart) {
        timelineStart = start
      }
      if (timelineEnd == null || complete > timelineEnd) {
        timelineEnd = complete
      }
    }

    const processFirstStart = new Map()
    for (const [key, tasks] of tasksByProcess) {
      processFirstStart.set(key, Math.min(...tasks.map(t => t.start.getTime())))
    }
    // Full process paths sorted chronologically
    const processKeys = [...tasksByProcess.keys()].sort(
      (a, b) => processFirstStart.get(a) - processFirstStart.get(b)
    )
    if (processKeys.length === 0) {
      return null
    }

    // Build the shortest suffix per process that is unique across all process paths.
    // e.g. ["A:B:C:FASTQ", "D:E:F:FASTQ"] → ["C:FASTQ", "F:FASTQ"]
    // e.g. ["A:FASTQ", "B:FASTQ"]           → ["A:FASTQ", "B:FASTQ"] (1 segment already unique)
    const processDisplayName = new Map()
    for (const key of processKeys) {
      const parts = key.split(':')
      let depth = 1
      let label = parts[parts.length - 1]
      while (
        depth < parts.length &&
        processKeys.filter(k => k.endsWith(':' + label) || k === label).length > 1
      ) {
        depth++
        label = parts.slice(parts.length - depth).join(':')
      }
      processDisplayName.set(key, label)
    }
    // Convenience alias for y-axis tick labels
    const processNames = processKeys.map(k => processDisplayName.get(k))

    function colorFromProcessName(name, alpha = 1.0) {
      let hash = 0
      for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash)
      }
      const hue = Math.abs(hash) % 360
      return `hsla(${hue}, 62%, 46%, ${alpha})`
    }

    const swimlaneData = []
    for (const [processIndex, processKey] of processKeys.entries()) {
      const displayName = processDisplayName.get(processKey)
      const processTasks = tasksByProcess.get(processKey).sort((a, b) => a.start - b.start)
      const laneEndTimes = []
      const laneAssignments = []

      // Greedy interval partitioning: overlapping tasks go to different sub-lanes.
      for (const task of processTasks) {
        let lane = -1
        for (let laneIndex = 0; laneIndex < laneEndTimes.length; laneIndex++) {
          if (task.start >= laneEndTimes[laneIndex]) {
            lane = laneIndex
            break
          }
        }

        if (lane === -1) {
          lane = laneEndTimes.length
          laneEndTimes.push(task.complete)
        } else {
          laneEndTimes[lane] = task.complete
        }

        laneAssignments.push(lane)
      }

      const laneCount = Math.max(1, laneEndTimes.length)
      const laneBandHalfHeight = 0.34
      const laneStep = laneCount === 1 ? 0 : (2 * laneBandHalfHeight) / (laneCount - 1)

      for (const [taskIndex, task] of processTasks.entries()) {
        const durationMinutes = (task.complete - task.start) / 60000.0
        const lane = laneAssignments[taskIndex]
        const laneY = laneCount === 1
          ? processIndex
          : processIndex - laneBandHalfHeight + lane * laneStep
        // Per-task traces with alpha make parallel overlap visually darker.
        swimlaneData.push({
          name: displayName,
          x: [task.start, task.complete],
          y: [laneY, laneY],
          type: 'scatter',
          mode: 'lines+markers',
          line: { width: 8, color: colorFromProcessName(displayName, 0.42) },
          marker: {
            size: 4,
            color: colorFromProcessName(displayName, 0.9),
            symbol: 'line-ns-open',
          },
          connectgaps: false,
          showlegend: false,
          hovertemplate: [
            `<b>${displayName}</b>`,
            `Task ID: ${task.task_id ?? 'n/a'}`,
            `Status: ${task.status ?? 'n/a'}`,
            `Duration: ${durationMinutes.toFixed(1)} min`,
            '<extra></extra>',
          ].join('<br>'),
        })
      }
    }

    const swimlaneLayout = {
      title: { text: 'Task execution swimlanes by process' },
      margin: { l: 140, r: 100, t: 40, b: 50 },
      height: Math.max(360, Math.min(1200, 150 + processNames.length * 28)),
      plot_bgcolor: '#FCFEFF',
      paper_bgcolor: '#FFFFFF',
      xaxis: {
        title: { text: 'Time' },
        type: 'date',
        range: timelineStart && timelineEnd ? [timelineStart, timelineEnd] : undefined,
        showgrid: true,
        gridcolor: 'rgba(36,64,87,0.08)',
        zeroline: false,
        showline: false,
        hoverformat: '%Y-%m-%d %H:%M',
      },
      yaxis: {
        title: { text: 'Process' },
        tickmode: 'array',
        tickvals: processNames.map((_, index) => index),
        ticktext: processNames,
        range: [-0.5, processNames.length - 0.5],
        autorange: 'reversed',
        showgrid: true,
        gridcolor: 'rgba(36,64,87,0.08)',
        zeroline: false,
        showline: false,
        automargin: true,
      },
      hovermode: 'closest',
      hoverlabel: { bgcolor: 'rgba(28,48,66,0.92)', bordercolor: 'rgba(28,48,66,0.0)', font: { size: 12, color: '#FFFFFF' }, align: 'left' },
    }

    return Plotly.newPlot(swimlanePlotContainer, swimlaneData, swimlaneLayout, { responsive: true })
  }

  function link_timeline_xaxis(ciGraphDiv, swimlaneGraphDiv) {
    if (!ciGraphDiv || !swimlaneGraphDiv) {
      return
    }

    if (ciGraphDiv._nfTimelineSyncAttached || swimlaneGraphDiv._nfTimelineSyncAttached) {
      return
    }

    ciGraphDiv._nfTimelineSyncAttached = true
    swimlaneGraphDiv._nfTimelineSyncAttached = true

    let syncInProgress = false

    const initialRange = ciGraphDiv.layout?.xaxis?.range
    if (Array.isArray(initialRange) && initialRange.length === 2) {
      Plotly.relayout(swimlaneGraphDiv, { 'xaxis.range': initialRange })
    }

    function mirrorRelayout(targetGraphDiv, eventData) {
      if (syncInProgress || !eventData) {
        return
      }

      if (eventData['xaxis.autorange']) {
        syncInProgress = true
        Plotly.relayout(targetGraphDiv, { 'xaxis.autorange': true })
          .then(() => {
            syncInProgress = false
          })
          .catch(() => {
            syncInProgress = false
          })
        return
      }

      let range0 = eventData['xaxis.range[0]']
      let range1 = eventData['xaxis.range[1]']
      if ((range0 === undefined || range1 === undefined) && Array.isArray(eventData['xaxis.range'])) {
        range0 = eventData['xaxis.range'][0]
        range1 = eventData['xaxis.range'][1]
      }

      if (range0 !== undefined && range1 !== undefined) {
        syncInProgress = true
        Plotly.relayout(targetGraphDiv, { 'xaxis.range': [range0, range1] })
          .then(() => {
            syncInProgress = false
          })
          .catch(() => {
            syncInProgress = false
          })
      }
    }

    ciGraphDiv.on('plotly_relayout', (eventData) => {
      mirrorRelayout(swimlaneGraphDiv, eventData)
    })
    ciGraphDiv.on('plotly_doubleclick', () => {
      if (!syncInProgress) {
        syncInProgress = true
        Plotly.relayout(swimlaneGraphDiv, { 'xaxis.autorange': true })
          .then(() => { syncInProgress = false })
          .catch(() => { syncInProgress = false })
      }
    })

    swimlaneGraphDiv.on('plotly_relayout', (eventData) => {
      mirrorRelayout(ciGraphDiv, eventData)
    })
    swimlaneGraphDiv.on('plotly_doubleclick', () => {
      if (!syncInProgress) {
        syncInProgress = true
        Plotly.relayout(ciGraphDiv, { 'xaxis.autorange': true })
          .then(() => { syncInProgress = false })
          .catch(() => { syncInProgress = false })
      }
    })
  }

  // Executor for ci plot generation
  Promise.all([
    Promise.resolve(make_ci_plot()),
    Promise.resolve(make_process_swimlane_plot()),
  ]).then(([ciGraphDiv, swimlaneGraphDiv]) => {
    link_timeline_xaxis(ciGraphDiv, swimlaneGraphDiv)
  })
})