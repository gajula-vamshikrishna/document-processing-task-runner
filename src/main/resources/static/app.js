const statsMap = {
  running: document.getElementById('runningStat'),
  waiting: document.getElementById('waitingStat'),
  succeeded: document.getElementById('succeededStat'),
  failed: document.getElementById('failedStat'),
  blocked: document.getElementById('blockedStat'),
  cancelled: document.getElementById('cancelledStat')
};

const form = document.getElementById('workflowForm');
const documentNameInput = document.getElementById('documentName');
const formMessage = document.getElementById('formMessage');
const taskTableBody = document.getElementById('taskTableBody');
const refreshTasksBtn = document.getElementById('refreshTasksBtn');
const submissionResult = document.getElementById('submissionResult');

const workflowTaskNames = [
  'validate-document',
  'extract-data',
  'classify-document',
  'validate-data',
  'generate-json',
  'store-result'
];

const taskStatuses = new Map();
const trackedTaskIds = [];

function setMessage(text, type = 'info') {
  formMessage.className = `message ${type}`;
  formMessage.textContent = text;
}

function setSubmissionResult(text) {
  submissionResult.classList.remove('hidden');
  submissionResult.innerHTML = text;
}

function getStatusClass(status) {
  const normalized = (status || '').toUpperCase();
  if (normalized === 'SUCCEEDED') return 'status-success';
  if (normalized === 'RUNNING') return 'status-running';
  if (normalized === 'WAITING' || normalized === 'RETRY_WAITING') return 'status-waiting';
  if (normalized === 'FAILED') return 'status-failed';
  if (normalized === 'BLOCKED') return 'status-blocked';
  if (normalized === 'CANCELLED') return 'status-cancelled';
  return 'status-neutral';
}

function renderWorkflowLegendStatus() {
  const nodes = document.querySelectorAll('.node');
  nodes.forEach((node) => {
    const taskName = node.dataset.taskName;
    const status = taskStatuses.get(taskName) || 'WAITING';
    node.classList.remove('status-success', 'status-running', 'status-waiting', 'status-failed', 'status-blocked', 'status-cancelled');
    node.classList.add(getStatusClass(status));
    const statusLabel = node.querySelector('.node-status');
    if (statusLabel) {
      statusLabel.textContent = status;
    }
  });
}

async function apiFetch(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (error) {
    payload = null;
  }

  if (!response.ok) {
    const message = payload && payload.message ? payload.message : payload && payload.error ? payload.error : 'Unexpected server error.';
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return payload;
}

async function loadStats() {
  try {
    const stats = await apiFetch('/api/tasks/stats');
    statsMap.running.textContent = stats.running || 0;
    statsMap.waiting.textContent = stats.waiting || 0;
    statsMap.succeeded.textContent = stats.succeeded || 0;
    statsMap.failed.textContent = stats.failed || 0;
    statsMap.blocked.textContent = stats.blocked || 0;
    statsMap.cancelled.textContent = stats.cancelled || 0;
  } catch (error) {
    console.error('Failed to load statistics', error);
  }
}

async function fetchTaskById(taskId) {
  return apiFetch(`/api/tasks/${taskId}`);
}

async function refreshTrackedTasks() {
  if (!trackedTaskIds.length) {
    taskTableBody.innerHTML = '<tr><td colspan="5" class="muted">No tasks loaded yet.</td></tr>';
    return;
  }

  try {
    const tasks = await Promise.all(trackedTaskIds.map((taskId) => fetchTaskById(taskId)));
    const taskRows = tasks
      .map((task) => {
        const status = task.status || 'WAITING';
        const statusClass = getStatusClass(status);
        const errorText = task.errorMessage || '—';
        taskStatuses.set(task.name, status);

        const taskName = task.name || 'Unknown';
        const canCancel = status !== 'RUNNING' && status !== 'SUCCEEDED' && status !== 'FAILED' && status !== 'BLOCKED' && status !== 'CANCELLED';

        return `
          <tr>
            <td>${taskName}</td>
            <td><span class="status-badge ${statusClass}">${status}</span></td>
            <td>${task.attempts || 0}</td>
            <td>${errorText}</td>
            <td>
              ${canCancel ? `<button class="cancel-btn" data-task-id="${task.id}" type="button">Cancel</button>` : '<span class="muted">—</span>'}
            </td>
          </tr>
        `;
      })
      .join('');

    taskTableBody.innerHTML = taskRows;
    renderWorkflowLegendStatus();

    document.querySelectorAll('.cancel-btn').forEach((button) => {
      button.addEventListener('click', async () => {
        const taskId = button.dataset.taskId;
        await cancelTask(taskId);
      });
    });
  } catch (error) {
    console.error('Failed to refresh tasks', error);
    taskTableBody.innerHTML = '<tr><td colspan="5" class="muted">Unable to load task state.</td></tr>';
  }
}

async function cancelTask(taskId) {
  try {
    await apiFetch(`/api/tasks/${taskId}/cancel`, { method: 'POST' });
    setMessage('Task cancellation requested.', 'success');
    await refreshTrackedTasks();
    await loadStats();
  } catch (error) {
    if (error.status === 409) {
      setMessage('This task is currently running and cannot be cancelled.', 'error');
      return;
    }

    setMessage('Unable to cancel task. Please try again.', 'error');
  }
}

async function submitWorkflow(event) {
  event.preventDefault();

  const documentName = documentNameInput.value.trim() || 'hotel_bill_1025.pdf';
  const payload = {
    document: {
      fileName: documentName
    },
    tasks: [
      { name: 'validate-document', type: 'VALIDATE_DOCUMENT', dependencies: [], maxAttempts: 3, failureProbability: 0.1 },
      { name: 'extract-data', type: 'EXTRACT_DATA', dependencies: ['validate-document'], maxAttempts: 3, failureProbability: 0.2 },
      { name: 'classify-document', type: 'CLASSIFY_DOCUMENT', dependencies: ['validate-document'], maxAttempts: 3, failureProbability: 0.1 },
      { name: 'validate-data', type: 'VALIDATE_DATA', dependencies: ['extract-data', 'classify-document'], maxAttempts: 3, failureProbability: 0.1 },
      { name: 'generate-json', type: 'GENERATE_JSON', dependencies: ['validate-data'], maxAttempts: 3, failureProbability: 0.05 },
      { name: 'store-result', type: 'STORE_RESULT', dependencies: ['generate-json'], maxAttempts: 3, failureProbability: 0.05 }
    ]
  };

  try {
    const result = await apiFetch('/api/workflows', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    trackedTaskIds.length = 0;
    trackedTaskIds.push(...(result.taskIds || []));

    setMessage('Workflow submitted successfully.', 'success');
    setSubmissionResult(`
      <strong>Workflow created</strong><br />
      Document ID: ${result.documentId}<br />
      Task IDs: ${(result.taskIds || []).join(', ')}
    `);

    workflowTaskNames.forEach((taskName) => taskStatuses.set(taskName, 'WAITING'));
    renderWorkflowLegendStatus();
    await refreshTrackedTasks();
    await loadStats();
  } catch (error) {
    const message = error.status === 400 ? 'Unable to submit workflow. Please check the document name or task data.' :
      error.status === 404 ? 'Workflow endpoint not found.' :
      error.status === 500 ? 'Unable to submit workflow. Please check that the backend is running.' :
      'Unable to submit workflow. Please check that the backend is running.';

    setMessage(message, 'error');
    setSubmissionResult('<strong>Submission failed.</strong>');
  }
}

async function pollDashboard() {
  await loadStats();
  if (trackedTaskIds.length) {
    await refreshTrackedTasks();
  }
}

form.addEventListener('submit', submitWorkflow);
refreshTasksBtn.addEventListener('click', async () => {
  await pollDashboard();
});

workflowTaskNames.forEach((taskName) => taskStatuses.set(taskName, 'WAITING'));
renderWorkflowLegendStatus();
loadStats();
setInterval(pollDashboard, 3000);
