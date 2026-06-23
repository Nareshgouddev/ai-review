import React from 'react';

function App(): React.ReactElement {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-4 sm:px-6 lg:px-8">
          <h1 className="text-2xl font-bold text-gray-900">AI UI Review Agent</h1>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-4 py-8 sm:px-6 lg:px-8">
        <p className="text-gray-600">
          Upload a UI screenshot to receive structured design feedback powered by AI.
        </p>
      </main>
    </div>
  );
}

export default App;
