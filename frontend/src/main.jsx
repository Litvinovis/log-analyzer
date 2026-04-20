import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider, theme } from 'antd'
import App from './App'
import 'antd/dist/reset.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <BrowserRouter>
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#4096ff',
          borderRadius: 6,
          colorBgContainer: '#1e1e2e',
          colorBgElevated: '#2a2a3e',
          colorBgLayout: '#13131f',
        },
      }}
    >
      <App />
    </ConfigProvider>
  </BrowserRouter>
)
