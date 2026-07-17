import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the admin shell and main navigation', () => {
    render(<App />)

    expect(screen.getByText('Grab Admin')).toBeInTheDocument()
    expect(screen.getAllByText('Tìm đường')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Khách hàng')[0]).toBeInTheDocument()
  })
})
