import { useEffect, useMemo, useRef, useState } from 'react'
import { Circle, MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import './App.css'

type ViewKey = 'route' | 'nearby' | 'customers' | 'drivers' | 'trips'
type RouteMode = 'driving' | 'cycling' | 'foot'
type DriverStatus = 'online' | 'offline' | 'busy'
type VehicleType = 'bike' | 'car' | 'car7' | 'truck'

type Customer = {
  id: string
  name: string
  phone: string
  email?: string | null
  address?: string | null
}

type Driver = {
  id: string
  name: string
  phone: string
  vehicle: VehicleType
  plate: string
  lat?: number | null
  lng?: number | null
  status?: DriverStatus
  rating?: number
  distance_km?: number
}

type Trip = {
  id: string
  customerId: string
  driverId?: string | null
  price?: number | null
  status?: string
  timeStart?: string | null
  timeEnd?: string | null
  latitudeStart?: number | null
  longitudeStart?: number | null
  latitudeEnd?: number | null
  longitudeEnd?: number | null
}

type ToastState = { message: string; isError: boolean; visible: boolean }
type PlaceSuggestion = { display_name: string; lat: number; lon: number }
type Point = { lat: number; lng: number; label: string }

type CustomerFormState = {
  id: string
  name: string
  phone: string
  email: string
  address: string
}

type DriverFormState = {
  id: string
  name: string
  phone: string
  vehicle: VehicleType
  plate: string
  lat: string
  lng: string
}

type TripFormState = {
  id: string
  customerId: string
  driverId: string
  price: string
  status: string
  timeStart: string
  timeEnd: string
  latStart: string
  lngStart: string
  latEnd: string
  lngEnd: string
}

const defaultCenter: [number, number] = [21.0285, 105.8542]
const greenIcon = L.divIcon({
  className: '',
  html: '<div style="width:16px;height:16px;border-radius:50%;background:#00B14F;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.3);"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
})
const blackIcon = L.divIcon({
  className: '',
  html: '<div style="width:16px;height:16px;border-radius:4px;background:#0B1D14;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.3);"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
})
const driverIcon = L.divIcon({
  className: '',
  html: '<div style="width:26px;height:26px;border-radius:50%;background:#fff;border:2px solid #00B14F;box-shadow:0 2px 6px rgba(0,0,0,0.3);display:flex;align-items:center;justify-content:center;font-size:13px;">🚗</div>',
  iconSize: [26, 26],
  iconAnchor: [13, 13],
})
const vehicleEmoji: Record<VehicleType, string> = { bike: '🏍️', car: '🚗', car7: '🚐', truck: '🚚' }
const vehicleLabel: Record<VehicleType, string> = { bike: 'Xe máy', car: 'Ô tô', car7: 'Ô tô 7 chỗ', truck: 'Xe tải' }
const statusLabel: Record<DriverStatus, string> = { online: 'Online', offline: 'Offline', busy: 'Đang bận' }

function App() {
  const [activeView, setActiveView] = useState<ViewKey>('route')
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [apiBase, setApiBase] = useState('http://localhost:3000')
  const [connectionState, setConnectionState] = useState({ connected: false, text: 'Đang kiểm tra...' })
  const [toast, setToast] = useState<ToastState>({ message: '', isError: false, visible: false })

  const [routeFrom, setRouteFrom] = useState<Point | null>(null)
  const [routeTo, setRouteTo] = useState<Point | null>(null)
  const [routeFromText, setRouteFromText] = useState('')
  const [routeToText, setRouteToText] = useState('')
  const [routeMode, setRouteMode] = useState<RouteMode>('driving')
  const [routePick, setRoutePick] = useState<'from' | 'to'>('from')
  const [routeLine, setRouteLine] = useState<[number, number][]>([])
  const [routeResult, setRouteResult] = useState<{ distance: string; time: string; info: string } | null>(null)
  const [routeSuggestions, setRouteSuggestions] = useState<PlaceSuggestion[]>([])
  const [routeSearchTarget, setRouteSearchTarget] = useState<'from' | 'to' | null>(null)
  const [routeSearchQuery, setRouteSearchQuery] = useState('')

  const [nearbyCenter, setNearbyCenter] = useState<[number, number] | null>(null)
  const [nearbyLabel, setNearbyLabel] = useState('')
  const [nearbyRadius, setNearbyRadius] = useState(5)
  const [nearbyVehicle, setNearbyVehicle] = useState('')
  const [nearbyResults, setNearbyResults] = useState<Driver[]>([])

  const [customers, setCustomers] = useState<Customer[]>([])
  const [customerQuery, setCustomerQuery] = useState('')
  const [customerModalOpen, setCustomerModalOpen] = useState(false)
  const [customerForm, setCustomerForm] = useState<CustomerFormState>({ id: '', name: '', phone: '', email: '', address: '' })

  const [drivers, setDrivers] = useState<Driver[]>([])
  const [driverStatusFilter, setDriverStatusFilter] = useState('')
  const [driverVehicleFilter, setDriverVehicleFilter] = useState('')
  const [driverModalOpen, setDriverModalOpen] = useState(false)
  const [driverForm, setDriverForm] = useState<DriverFormState>({ id: '', name: '', phone: '', vehicle: 'bike', plate: '', lat: '', lng: '' })

  const [trips, setTrips] = useState<Trip[]>([])
  const [tripQuery, setTripQuery] = useState('')
  const [tripModalOpen, setTripModalOpen] = useState(false)
  const [tripForm, setTripForm] = useState<TripFormState>({
    id: '',
    customerId: '',
    driverId: '',
    price: '',
    status: 'requested',
    timeStart: '',
    timeEnd: '',
    latStart: '',
    lngStart: '',
    latEnd: '',
    lngEnd: '',
  })
  const [tripCustomers, setTripCustomers] = useState<Customer[]>([])
  const [tripDrivers, setTripDrivers] = useState<Driver[]>([])

  const toastTimer = useRef<number | null>(null)

  const showToast = (message: string, isError = false) => {
    setToast({ message, isError, visible: true })
  }

  const api = async (path: string, opts: RequestInit = {}) => {
    const res = await fetch(`${apiBase}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...opts,
    })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error((data as { error?: string }).error || 'Lỗi không xác định')
    return data
  }

  const checkConnection = async () => {
    try {
      await api('/')
      setConnectionState({ connected: true, text: 'Đã kết nối backend' })
    } catch {
      setConnectionState({ connected: false, text: 'Chưa kết nối backend' })
    }
  }

  useEffect(() => {
    void checkConnection()
    const interval = window.setInterval(() => {
      void checkConnection()
    }, 15000)
    return () => window.clearInterval(interval)
  }, [apiBase])

  useEffect(() => {
    if (!toast.visible) return
    if (toastTimer.current) window.clearTimeout(toastTimer.current)
    toastTimer.current = window.setTimeout(() => {
      setToast((current) => ({ ...current, visible: false }))
    }, 3000)
    return () => {
      if (toastTimer.current) window.clearTimeout(toastTimer.current)
    }
  }, [toast.visible])

  useEffect(() => {
    if (activeView === 'customers') {
      void loadCustomers()
    }
    if (activeView === 'drivers') {
      void loadDrivers()
    }
    if (activeView === 'trips') {
      void loadTrips()
    }
  }, [activeView])

  useEffect(() => {
    if (activeView === 'customers') {
      void loadCustomers()
    }
  }, [customerQuery, activeView])

  useEffect(() => {
    if (activeView === 'drivers') {
      void loadDrivers()
    }
  }, [driverStatusFilter, driverVehicleFilter, activeView])

  useEffect(() => {
    if (activeView === 'trips') {
      void loadTrips()
    }
  }, [tripQuery, activeView])

  useEffect(() => {
    if (routeSearchQuery.length < 3) {
      setRouteSuggestions([])
      return
    }
    const timer = window.setTimeout(() => {
      void searchPlaces(routeSearchQuery)
    }, 400)
    return () => window.clearTimeout(timer)
  }, [routeSearchQuery, routeSearchTarget])

  const loadCustomers = async () => {
    const q = customerQuery.trim()
    try {
      const params = q ? `?q=${encodeURIComponent(q)}` : ''
      const data = await api(`/api/customers${params}`)
      setCustomers(data.data || [])
    } catch (error) {
      showToast(`Không tải được danh sách khách hàng: ${(error as Error).message}`, true)
    }
  }

  const loadDrivers = async () => {
    try {
      const params = new URLSearchParams()
      if (driverStatusFilter) params.set('status', driverStatusFilter)
      if (driverVehicleFilter) params.set('vehicle', driverVehicleFilter)
      const data = await api(`/api/drivers${params.toString() ? `?${params.toString()}` : ''}`)
      setDrivers(data.data || [])
    } catch (error) {
      showToast(`Không tải được danh sách tài xế: ${(error as Error).message}`, true)
    }
  }

  const loadTrips = async () => {
    try {
      const [tripRes, custRes, drvRes] = await Promise.all([api('/api/trips'), api('/api/customers'), api('/api/drivers')])
      const tripsData = tripRes.data || []
      const customerMap = Object.fromEntries((custRes.data || []).map((entry: Customer) => [entry.id, entry]))
      const driverMap = Object.fromEntries((drvRes.data || []).map((entry: Driver) => [entry.id, entry]))
      const query = tripQuery.trim().toLowerCase()
      const filtered = query
        ? tripsData.filter((trip: Trip) => {
            const customerName = customerMap[trip.customerId]?.name?.toLowerCase() || ''
            const driverName = trip.driverId && driverMap[trip.driverId]?.name?.toLowerCase() || ''
            return trip.id.toLowerCase().includes(query) || customerName.includes(query) || driverName.includes(query)
          })
        : tripsData
      setTrips(filtered)
      setTripCustomers(custRes.data || [])
      setTripDrivers(drvRes.data || [])
    } catch (error) {
      showToast(`Không tải được danh sách chuyến: ${(error as Error).message}`, true)
    }
  }

  const searchPlaces = async (query: string) => {
    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&addressdetails=1`
      const res = await fetch(url, { headers: { 'Accept-Language': 'vi' } })
      const data = await res.json()
      setRouteSuggestions(data)
    } catch {
      setRouteSuggestions([])
    }
  }

  const setPoint = (target: 'from' | 'to', point: Point) => {
    if (target === 'from') {
      setRouteFrom(point)
      setRouteFromText(point.label)
    } else {
      setRouteTo(point)
      setRouteToText(point.label)
    }
    setRouteSuggestions([])
  }

  const handleMapClick = (lat: number, lng: number) => {
    const point = { lat, lng, label: `${lat.toFixed(5)}, ${lng.toFixed(5)}` }
    setPoint(routePick, point)
    setRoutePick(routePick === 'from' ? 'to' : 'from')
  }

  const handleFindRoute = async () => {
    if (!routeFrom || !routeTo) return
    try {
      const coords = `${routeFrom.lng},${routeFrom.lat};${routeTo.lng},${routeTo.lat}`
      const url = `https://router.project-osrm.org/route/v1/${routeMode}/${coords}?overview=full&geometries=geojson`
      const res = await fetch(url)
      const data = await res.json()
      if (data.code !== 'Ok') {
        showToast('Không tìm được đường đi', true)
        return
      }
      const route = data.routes[0]
      const line = route.geometry.coordinates.map((coord: number[]) => [coord[1], coord[0]] as [number, number])
      setRouteLine(line)
      const distance = (route.distance / 1000).toFixed(2)
      const minutes = Math.round(route.duration / 60)
      const hours = Math.floor(minutes / 60)
      const remainingMinutes = minutes % 60
      setRouteResult({
        distance,
        time: `${hours > 0 ? `${hours} giờ ${remainingMinutes} phút` : `${minutes} phút`}`,
        info: `<b>Từ:</b> ${routeFromText}<br><b>Đến:</b> ${routeToText}`,
      })
      showToast('Đã tìm thấy lộ trình')
    } catch {
      showToast('Lỗi khi tìm đường', true)
    }
  }

  const handleUseLocation = () => {
    if (!navigator.geolocation) {
      showToast('Trình duyệt không hỗ trợ định vị', true)
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const point = { lat: position.coords.latitude, lng: position.coords.longitude, label: 'Vị trí hiện tại của bạn' }
        setNearbyCenter([point.lat, point.lng])
        setNearbyLabel(point.label)
        setNearbyVehicle('')
      },
      () => showToast('Không lấy được vị trí. Hãy chọn trên bản đồ.', true),
    )
  }

  const handleFindNearby = async () => {
    if (!nearbyCenter) return
    try {
      const params = new URLSearchParams({ lat: String(nearbyCenter[0]), lng: String(nearbyCenter[1]), radius_km: String(nearbyRadius), status: 'online' })
      if (nearbyVehicle) params.set('vehicle', nearbyVehicle)
      const data = await api(`/api/drivers/nearby?${params.toString()}`)
      setNearbyResults(data.data || [])
      showToast(`Tìm thấy ${data.count} tài xế`)
    } catch (error) {
      showToast((error as Error).message || 'Lỗi khi tìm tài xế', true)
    }
  }

  const openCustomerModal = () => {
    setCustomerForm({ id: '', name: '', phone: '', email: '', address: '' })
    setCustomerModalOpen(true)
  }

  const editCustomer = async (id: string) => {
    try {
      const data = await api(`/api/customers/${id}`)
      setCustomerForm({ id: data.id, name: data.name, phone: data.phone, email: data.email || '', address: data.address || '' })
      setCustomerModalOpen(true)
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const saveCustomer = async () => {
    const payload = { name: customerForm.name.trim(), phone: customerForm.phone.trim(), email: customerForm.email.trim() || null, address: customerForm.address.trim() || null }
    if (!payload.name || !payload.phone) {
      showToast('Vui lòng nhập tên và số điện thoại', true)
      return
    }
    try {
      if (customerForm.id) {
        await api(`/api/customers/${customerForm.id}`, { method: 'PUT', body: JSON.stringify(payload) })
        showToast('Đã cập nhật khách hàng')
      } else {
        await api('/api/customers', { method: 'POST', body: JSON.stringify(payload) })
        showToast('Đã thêm khách hàng')
      }
      setCustomerModalOpen(false)
      void loadCustomers()
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const deleteCustomer = async (id: string) => {
    if (!window.confirm('Xoá khách hàng này?')) return
    try {
      await api(`/api/customers/${id}`, { method: 'DELETE' })
      showToast('Đã xoá khách hàng')
      void loadCustomers()
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const openDriverModal = () => {
    setDriverForm({ id: '', name: '', phone: '', vehicle: 'bike', plate: '', lat: '', lng: '' })
    setDriverModalOpen(true)
  }

  const editDriver = async (id: string) => {
    try {
      const data = await api(`/api/drivers/${id}`)
      setDriverForm({ id: data.id, name: data.name, phone: data.phone, vehicle: data.vehicle, plate: data.plate, lat: data.lat ?? '', lng: data.lng ?? '' })
      setDriverModalOpen(true)
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const saveDriver = async () => {
    const payload: Record<string, unknown> = { name: driverForm.name.trim(), phone: driverForm.phone.trim(), vehicle: driverForm.vehicle, plate: driverForm.plate.trim() }
    if (!payload.name || !payload.phone || !payload.plate) {
      showToast('Vui lòng nhập đầy đủ thông tin bắt buộc', true)
      return
    }
    try {
      if (driverForm.id) {
        await api(`/api/drivers/${driverForm.id}`, { method: 'PUT', body: JSON.stringify(payload) })
        if (driverForm.lat && driverForm.lng) {
          await api(`/api/drivers/${driverForm.id}/location`, { method: 'PATCH', body: JSON.stringify({ lat: Number(driverForm.lat), lng: Number(driverForm.lng) }) })
        }
        showToast('Đã cập nhật tài xế')
      } else {
        if (driverForm.lat) payload.lat = Number(driverForm.lat)
        if (driverForm.lng) payload.lng = Number(driverForm.lng)
        await api('/api/drivers', { method: 'POST', body: JSON.stringify(payload) })
        showToast('Đã thêm tài xế')
      }
      setDriverModalOpen(false)
      void loadDrivers()
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const deleteDriver = async (id: string) => {
    if (!window.confirm('Xoá tài xế này?')) return
    try {
      await api(`/api/drivers/${id}`, { method: 'DELETE' })
      showToast('Đã xoá tài xế')
      void loadDrivers()
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const updateDriverStatus = async (id: string, status: DriverStatus) => {
    try {
      await api(`/api/drivers/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
      showToast('Đã cập nhật trạng thái')
    } catch (error) {
      showToast((error as Error).message, true)
      void loadDrivers()
    }
  }

  const openTripModal = async () => {
    try {
      const [custRes, drvRes] = await Promise.all([api('/api/customers'), api('/api/drivers')])
      setTripCustomers(custRes.data || [])
      setTripDrivers(drvRes.data || [])
      setTripForm({ id: '', customerId: '', driverId: '', price: '', status: 'requested', timeStart: '', timeEnd: '', latStart: '', lngStart: '', latEnd: '', lngEnd: '' })
      setTripModalOpen(true)
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const editTrip = async (id: string) => {
    try {
      const [tripRes, custRes, drvRes] = await Promise.all([api(`/api/trips/${id}`), api('/api/customers'), api('/api/drivers')])
      setTripCustomers(custRes.data || [])
      setTripDrivers(drvRes.data || [])
      setTripForm({
        id: tripRes.id,
        customerId: tripRes.customerId || '',
        driverId: tripRes.driverId || '',
        price: tripRes.price ?? '',
        status: tripRes.status || 'requested',
        timeStart: tripRes.timeStart ? new Date(tripRes.timeStart).toISOString().slice(0, 16) : '',
        timeEnd: tripRes.timeEnd ? new Date(tripRes.timeEnd).toISOString().slice(0, 16) : '',
        latStart: tripRes.latitudeStart ?? '',
        lngStart: tripRes.longitudeStart ?? '',
        latEnd: tripRes.latitudeEnd ?? '',
        lngEnd: tripRes.longitudeEnd ?? '',
      })
      setTripModalOpen(true)
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const saveTrip = async () => {
    const payload = {
      customerId: tripForm.customerId,
      driverId: tripForm.driverId || null,
      price: tripForm.price ? Number(tripForm.price) : null,
      status: tripForm.status,
      timeStart: tripForm.timeStart ? new Date(tripForm.timeStart).toISOString() : null,
      timeEnd: tripForm.timeEnd ? new Date(tripForm.timeEnd).toISOString() : null,
      latitudeStart: tripForm.latStart ? Number(tripForm.latStart) : null,
      longitudeStart: tripForm.lngStart ? Number(tripForm.lngStart) : null,
      latitudeEnd: tripForm.latEnd ? Number(tripForm.latEnd) : null,
      longitudeEnd: tripForm.lngEnd ? Number(tripForm.lngEnd) : null,
    }
    if (!payload.customerId) {
      showToast('Vui lòng chọn khách hàng', true)
      return
    }
    try {
      if (tripForm.id) {
        await api(`/api/trips/${tripForm.id}`, { method: 'PUT', body: JSON.stringify(payload) })
        showToast('Đã cập nhật chuyến đi')
      } else {
        await api('/api/trips', { method: 'POST', body: JSON.stringify(payload) })
        showToast('Đã thêm chuyến đi')
      }
      setTripModalOpen(false)
      void loadTrips()
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const deleteTrip = async (id: string) => {
    if (!window.confirm('Xoá chuyến đi này?')) return
    try {
      await api(`/api/trips/${id}`, { method: 'DELETE' })
      showToast('Đã xoá chuyến đi')
      void loadTrips()
    } catch (error) {
      showToast((error as Error).message, true)
    }
  }

  const routeBounds = useMemo(() => {
    const positions = [routeFrom ? [routeFrom.lat, routeFrom.lng] as [number, number] : null, routeTo ? [routeTo.lat, routeTo.lng] as [number, number] : null].filter(Boolean) as [number, number][]
    return positions
  }, [routeFrom, routeTo])

  const nearbyBounds = useMemo(() => {
    const positions = nearbyResults.map((driver) => [driver.lat ?? defaultCenter[0], driver.lng ?? defaultCenter[1]] as [number, number])
    return positions
  }, [nearbyResults])

  return (
    <div id="app">
      <aside id="sidebar" className={sidebarOpen ? '' : ''}>
        <div className="brand">
          <div className="dot" />
          <span>Grab Admin</span>
        </div>
        <div className={`nav-item ${activeView === 'route' ? 'active' : ''}`} onClick={() => { setActiveView('route'); setSidebarOpen(false) }}>
          <span className="nav-icon">🗺️</span> Tìm đường
        </div>
        <div className={`nav-item ${activeView === 'nearby' ? 'active' : ''}`} onClick={() => { setActiveView('nearby'); setSidebarOpen(false) }}>
          <span className="nav-icon">📍</span> Tìm tài xế quanh đây
        </div>
        <div className={`nav-item ${activeView === 'customers' ? 'active' : ''}`} onClick={() => { setActiveView('customers'); setSidebarOpen(false) }}>
          <span className="nav-icon">👥</span> Khách hàng
        </div>
        <div className={`nav-item ${activeView === 'drivers' ? 'active' : ''}`} onClick={() => { setActiveView('drivers'); setSidebarOpen(false) }}>
          <span className="nav-icon">🚗</span> Tài xế
        </div>
        <div className={`nav-item ${activeView === 'trips' ? 'active' : ''}`} onClick={() => { setActiveView('trips'); setSidebarOpen(false) }}>
          <span className="nav-icon">🧾</span> Danh sách chuyến đi
        </div>
        <div className="sidebar-footer">
          <div className="conn-row">
            <div className={`conn-dot ${connectionState.connected ? 'ok' : ''}`} />
            <span>{connectionState.text}</span>
          </div>
          <input id="apiInput" value={apiBase} onChange={(event) => setApiBase(event.target.value)} spellCheck={false} />
        </div>
      </aside>

      <main id="main">
        <div className={`view ${activeView === 'route' ? 'active' : ''}`} id="view-route">
          <div id="routeMap">
            <MapContainer center={defaultCenter} zoom={13} style={{ height: '100%', width: '100%' }}>
              <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution="&copy; OpenStreetMap" />
              {routeFrom ? <Marker position={[routeFrom.lat, routeFrom.lng]} icon={greenIcon} /> : null}
              {routeTo ? <Marker position={[routeTo.lat, routeTo.lng]} icon={blackIcon} /> : null}
              {routeLine.length > 0 ? <Polyline positions={routeLine} pathOptions={{ color: '#00B14F', weight: 5, opacity: 0.85 }} /> : null}
              <MapClickHandler onClick={handleMapClick} />
              {routeBounds.length > 0 ? <FitBounds positions={routeBounds} /> : null}
            </MapContainer>
          </div>
          <div className="panel">
            <div className="panel-header">Tìm đường & đo khoảng cách</div>
            <div className="field-group">
              <div className="field-row">
                <div className="fdot from" />
                <input value={routeFromText} placeholder="Điểm đi" autoComplete="off" onChange={(event) => { setRouteSearchTarget('from'); setRouteSearchQuery(event.target.value); setRouteFromText(event.target.value); }} />
              </div>
              <div className={`suggestions ${routeSuggestions.length > 0 && routeSearchTarget === 'from' ? 'show' : ''}`}>
                {routeSuggestions.map((place) => (
                  <div className="suggestion-item" key={`${place.display_name}-${place.lat}`} onClick={() => { setPoint('from', { lat: place.lat, lng: place.lon, label: place.display_name }); setRouteSearchTarget(null); setRouteSearchQuery(''); }}>
                    {place.display_name}
                  </div>
                ))}
              </div>
              <div className="field-row">
                <div className="fdot to" />
                <input value={routeToText} placeholder="Điểm đến" autoComplete="off" onChange={(event) => { setRouteSearchTarget('to'); setRouteSearchQuery(event.target.value); setRouteToText(event.target.value); }} />
              </div>
              <div className={`suggestions ${routeSuggestions.length > 0 && routeSearchTarget === 'to' ? 'show' : ''}`}>
                {routeSuggestions.map((place) => (
                  <div className="suggestion-item" key={`${place.display_name}-${place.lat}`} onClick={() => { setPoint('to', { lat: place.lat, lng: place.lon, label: place.display_name }); setRouteSearchTarget(null); setRouteSearchQuery(''); }}>
                    {place.display_name}
                  </div>
                ))}
              </div>
            </div>
            <div className="mode-tabs">
              {(['driving', 'cycling', 'foot'] as const).map((mode) => (
                <div key={mode} className={`mode-tab ${routeMode === mode ? 'active' : ''}`} onClick={() => setRouteMode(mode)}>{mode === 'driving' ? '🚗 Xe' : mode === 'cycling' ? '🚴 Đạp' : '🚶 Bộ'}</div>
              ))}
            </div>
            <button className="btn" onClick={handleFindRoute} disabled={!routeFrom || !routeTo}>Tìm đường</button>
          </div>
          <div className={`result-card ${routeResult ? 'show' : ''}`}>
            <div className="result-dist">{routeResult?.distance ?? '–'}</div>
            <div className="result-unit" style={{ marginTop: '-20px', marginLeft: 0 }}>km</div>
            <div className="result-time">⏱ {routeResult?.time ?? '–'}</div>
            <div className="result-route" dangerouslySetInnerHTML={{ __html: routeResult?.info ?? '' }} />
          </div>
        </div>

        <div className={`view ${activeView === 'nearby' ? 'active' : ''}`} id="view-nearby">
          <div id="nearbyMap">
            <MapContainer center={defaultCenter} zoom={13} style={{ height: '100%', width: '100%' }}>
              <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution="&copy; OpenStreetMap" />
              {nearbyCenter ? <Marker position={nearbyCenter} icon={greenIcon} /> : null}
              {nearbyCenter ? <Circle center={nearbyCenter} radius={nearbyRadius * 1000} pathOptions={{ color: '#00B14F', fillOpacity: 0.06, weight: 1.5 }} /> : null}
              {nearbyResults.map((driver) => (driver.lat && driver.lng ? <Marker key={driver.id} position={[driver.lat, driver.lng]} icon={driverIcon}><Popup><b>{driver.name}</b><br />{driver.plate}<br />{driver.distance_km} km</Popup></Marker> : null))}
              <MapClickHandler onClick={(lat, lng) => { setNearbyCenter([lat, lng]); setNearbyLabel(`${lat.toFixed(5)}, ${lng.toFixed(5)}`); }} />
              {nearbyCenter ? <FitBounds positions={nearbyBounds.length > 0 ? nearbyBounds : [[nearbyCenter[0], nearbyCenter[1]]] } /> : null}
            </MapContainer>
          </div>
          <div className="nearby-panel">
            <h3>Tìm tài xế quanh đây</h3>
            <div className="sub">Nhấp vào bản đồ để chọn vị trí của bạn, hoặc dùng vị trí hiện tại.</div>
            <div className="field-group" style={{ padding: 0, marginBottom: 10 }}>
              <div className="field-row">
                <div className="fdot from" />
                <input value={nearbyLabel} placeholder="Vị trí của bạn" autoComplete="off" onChange={(event) => setNearbyLabel(event.target.value)} />
              </div>
            </div>
            <button className="btn secondary" onClick={handleUseLocation} style={{ margin: '0 0 12px', width: '100%' }}>📍 Dùng vị trí hiện tại</button>
            <div className="slider-row"><span>Bán kính</span><span>{nearbyRadius} km</span></div>
            <input type="range" min="1" max="20" value={nearbyRadius} onChange={(event) => setNearbyRadius(Number(event.target.value))} />
            <div className="form-field">
              <label>Loại xe</label>
              <select value={nearbyVehicle} onChange={(event) => setNearbyVehicle(event.target.value)}>
                <option value="">Tất cả</option>
                <option value="bike">Xe máy</option>
                <option value="car">Ô tô</option>
                <option value="car7">Ô tô 7 chỗ</option>
                <option value="truck">Xe tải</option>
              </select>
            </div>
            <button className="btn" onClick={handleFindNearby} style={{ margin: '6px 0 4px', width: '100%' }}>Tìm tài xế</button>
            <div id="nResults" style={{ marginTop: 10 }}>
              {nearbyResults.length === 0 ? <div className="empty-state" style={{ padding: 16 }}>Không tìm thấy tài xế nào trong bán kính này.</div> : nearbyResults.map((driver) => (
                <div key={driver.id} className="driver-result" onClick={() => driver.lat && driver.lng ? setNearbyCenter([driver.lat, driver.lng]) : null}>
                  <div className="driver-avatar">{vehicleEmoji[driver.vehicle] || '🚗'}</div>
                  <div className="driver-info">
                    <div className="driver-name">{driver.name}</div>
                    <div className="driver-meta">{driver.plate} · ⭐ {driver.rating}</div>
                  </div>
                  <div className="driver-dist">{driver.distance_km} km</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className={`view ${activeView === 'customers' ? 'active' : ''}`} id="view-customers">
          <div className="crud-view">
            <div className="crud-header">
              <div>
                <h2>Khách hàng</h2>
                <div className="sub">{customers.length} khách hàng</div>
              </div>
              <button className="btn secondary" style={{ width: 'auto', margin: 0, padding: '9px 16px' }} onClick={openCustomerModal}>+ Thêm khách hàng</button>
            </div>
            <div className="toolbar">
              <input className="search-box" value={customerQuery} onChange={(event) => { setCustomerQuery(event.target.value); }} placeholder="Tìm theo tên, SĐT, email..." />
            </div>
            <table>
              <thead>
                <tr><th>Tên</th><th>SĐT</th><th>Email</th><th>Địa chỉ</th><th></th></tr>
              </thead>
              <tbody>
                {customers.length === 0 ? <tr><td colSpan={5}><div className="empty-state">Chưa có khách hàng nào. Nhấn "+ Thêm khách hàng" để bắt đầu.</div></td></tr> : customers.map((customer) => (
                  <tr key={customer.id}>
                    <td>{customer.name}</td>
                    <td>{customer.phone}</td>
                    <td>{customer.email || '–'}</td>
                    <td>{customer.address || '–'}</td>
                    <td><div className="row-actions"><button className="icon-btn" onClick={() => void editCustomer(customer.id)} title="Sửa">✏️</button><button className="icon-btn danger" onClick={() => void deleteCustomer(customer.id)} title="Xoá">🗑️</button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className={`view ${activeView === 'drivers' ? 'active' : ''}`} id="view-drivers">
          <div className="crud-view">
            <div className="crud-header">
              <div>
                <h2>Tài xế</h2>
                <div className="sub">{drivers.length} tài xế</div>
              </div>
              <button className="btn secondary" style={{ width: 'auto', margin: 0, padding: '9px 16px' }} onClick={openDriverModal}>+ Thêm tài xế</button>
            </div>
            <div className="toolbar">
              <select className="filter-box" value={driverStatusFilter} onChange={(event) => { setDriverStatusFilter(event.target.value); }}>
                <option value="">Tất cả trạng thái</option>
                <option value="online">Online</option>
                <option value="offline">Offline</option>
                <option value="busy">Đang bận</option>
              </select>
              <select className="filter-box" value={driverVehicleFilter} onChange={(event) => { setDriverVehicleFilter(event.target.value); }}>
                <option value="">Tất cả loại xe</option>
                <option value="bike">Xe máy</option>
                <option value="car">Ô tô</option>
                <option value="car7">Ô tô 7 chỗ</option>
                <option value="truck">Xe tải</option>
              </select>
            </div>
            <table>
              <thead>
                <tr><th>Tên</th><th>SĐT</th><th>Xe</th><th>Biển số</th><th>Trạng thái</th><th>Đánh giá</th><th></th></tr>
              </thead>
              <tbody>
                {drivers.length === 0 ? <tr><td colSpan={7}><div className="empty-state">Chưa có tài xế nào.</div></td></tr> : drivers.map((driver) => (
                  <tr key={driver.id}>
                    <td>{driver.name}</td>
                    <td>{driver.phone}</td>
                    <td>{vehicleLabel[driver.vehicle] || driver.vehicle}</td>
                    <td>{driver.plate}</td>
                    <td>
                      <select className="status-select" value={driver.status || 'online'} onChange={(event) => void updateDriverStatus(driver.id, event.target.value as DriverStatus)}>
                        {(['online', 'offline', 'busy'] as const).map((status) => <option key={status} value={status}>{statusLabel[status]}</option>)}
                      </select>
                    </td>
                    <td>⭐ {driver.rating}</td>
                    <td><div className="row-actions"><button className="icon-btn" onClick={() => void editDriver(driver.id)} title="Sửa">✏️</button><button className="icon-btn danger" onClick={() => void deleteDriver(driver.id)} title="Xoá">🗑️</button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className={`view ${activeView === 'trips' ? 'active' : ''}`} id="view-trips">
          <div className="crud-view">
            <div className="crud-header">
              <div>
                <h2>Danh sách chuyến đi</h2>
                <div className="sub">{trips.length} chuyến</div>
              </div>
              <button className="btn secondary" style={{ width: 'auto', margin: 0, padding: '9px 16px' }} onClick={() => void openTripModal()}>+ Thêm chuyến đi</button>
            </div>
            <div className="toolbar">
              <input className="search-box" value={tripQuery} onChange={(event) => { setTripQuery(event.target.value); }} placeholder="Tìm theo mã, khách hàng, tài xế..." />
            </div>
            <table>
              <thead>
                <tr><th>Mã</th><th>Khách hàng</th><th>Tài xế</th><th>Giá</th><th>Bắt đầu</th><th>Kết thúc</th><th>Trạng thái</th><th></th></tr>
              </thead>
              <tbody>
                {trips.length === 0 ? <tr><td colSpan={8}><div className="empty-state">Chưa có chuyến đi nào.</div></td></tr> : trips.map((trip) => (
                  <tr key={trip.id}>
                    <td>{trip.id}</td>
                    <td>{tripCustomers.find((customer) => customer.id === trip.customerId)?.name || trip.customerId}</td>
                    <td>{tripDrivers.find((driver) => driver.id === trip.driverId)?.name || '–'}</td>
                    <td>{trip.price != null ? `${trip.price} đ` : '–'}</td>
                    <td>{trip.timeStart ? new Date(trip.timeStart).toLocaleString() : '–'}</td>
                    <td>{trip.timeEnd ? new Date(trip.timeEnd).toLocaleString() : '–'}</td>
                    <td>{trip.status || '–'}</td>
                    <td><div className="row-actions"><button className="icon-btn" onClick={() => void editTrip(trip.id)} title="Sửa">✏️</button><button className="icon-btn danger" onClick={() => void deleteTrip(trip.id)} title="Xoá">🗑️</button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </main>

      <div className={`modal-overlay ${customerModalOpen ? 'show' : ''}`} onClick={(event) => event.target === event.currentTarget && setCustomerModalOpen(false)}>
        <div className="modal">
          <h3>{customerForm.id ? 'Sửa khách hàng' : 'Thêm khách hàng'}</h3>
          <input type="hidden" value={customerForm.id} />
          <div className="form-field"><label>Họ tên *</label><input value={customerForm.name} onChange={(event) => setCustomerForm((current) => ({ ...current, name: event.target.value }))} placeholder="Nguyễn Văn A" /></div>
          <div className="form-field"><label>Số điện thoại *</label><input value={customerForm.phone} onChange={(event) => setCustomerForm((current) => ({ ...current, phone: event.target.value }))} placeholder="0901234567" /></div>
          <div className="form-field"><label>Email</label><input value={customerForm.email} onChange={(event) => setCustomerForm((current) => ({ ...current, email: event.target.value }))} placeholder="a@example.com" /></div>
          <div className="form-field"><label>Địa chỉ</label><input value={customerForm.address} onChange={(event) => setCustomerForm((current) => ({ ...current, address: event.target.value }))} placeholder="12 Hàng Bài, Hà Nội" /></div>
          <div className="modal-actions"><button className="btn secondary" onClick={() => setCustomerModalOpen(false)}>Huỷ</button><button className="btn" onClick={() => void saveCustomer()}>Lưu</button></div>
        </div>
      </div>

      <div className={`modal-overlay ${driverModalOpen ? 'show' : ''}`} onClick={(event) => event.target === event.currentTarget && setDriverModalOpen(false)}>
        <div className="modal">
          <h3>{driverForm.id ? 'Sửa tài xế' : 'Thêm tài xế'}</h3>
          <input type="hidden" value={driverForm.id} />
          <div className="form-field"><label>Họ tên *</label><input value={driverForm.name} onChange={(event) => setDriverForm((current) => ({ ...current, name: event.target.value }))} placeholder="Lê Văn B" /></div>
          <div className="form-field"><label>Số điện thoại *</label><input value={driverForm.phone} onChange={(event) => setDriverForm((current) => ({ ...current, phone: event.target.value }))} placeholder="0912345678" /></div>
          <div className="form-row">
            <div className="form-field"><label>Loại xe *</label><select value={driverForm.vehicle} onChange={(event) => setDriverForm((current) => ({ ...current, vehicle: event.target.value as VehicleType }))}><option value="bike">Xe máy</option><option value="car">Ô tô</option><option value="car7">Ô tô 7 chỗ</option><option value="truck">Xe tải</option></select></div>
            <div className="form-field"><label>Biển số *</label><input value={driverForm.plate} onChange={(event) => setDriverForm((current) => ({ ...current, plate: event.target.value }))} placeholder="29A1-123.45" /></div>
          </div>
          <div className="form-row">
            <div className="form-field"><label>Vĩ độ (lat)</label><input value={driverForm.lat} onChange={(event) => setDriverForm((current) => ({ ...current, lat: event.target.value }))} placeholder="21.0285" /></div>
            <div className="form-field"><label>Kinh độ (lng)</label><input value={driverForm.lng} onChange={(event) => setDriverForm((current) => ({ ...current, lng: event.target.value }))} placeholder="105.8542" /></div>
          </div>
          <div className="modal-actions"><button className="btn secondary" onClick={() => setDriverModalOpen(false)}>Huỷ</button><button className="btn" onClick={() => void saveDriver()}>Lưu</button></div>
        </div>
      </div>

      <div className={`modal-overlay ${tripModalOpen ? 'show' : ''}`} onClick={(event) => event.target === event.currentTarget && setTripModalOpen(false)}>
        <div className="modal">
          <h3>{tripForm.id ? 'Sửa chuyến đi' : 'Thêm chuyến đi'}</h3>
          <div className="form-field"><label>Khách hàng *</label><select value={tripForm.customerId} onChange={(event) => setTripForm((current) => ({ ...current, customerId: event.target.value }))}>{tripCustomers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name} · {customer.phone}</option>)}</select></div>
          <div className="form-field"><label>Tài xế</label><select value={tripForm.driverId} onChange={(event) => setTripForm((current) => ({ ...current, driverId: event.target.value }))}><option value="">— Chọn tài xế —</option>{tripDrivers.map((driver) => <option key={driver.id} value={driver.id}>{driver.name} · {driver.plate}</option>)}</select></div>
          <div className="form-row">
            <div className="form-field"><label>Giá tiền</label><input value={tripForm.price} onChange={(event) => setTripForm((current) => ({ ...current, price: event.target.value }))} placeholder="120000" /></div>
            <div className="form-field"><label>Trạng thái</label><select value={tripForm.status} onChange={(event) => setTripForm((current) => ({ ...current, status: event.target.value }))}><option value="requested">Yêu cầu</option><option value="ongoing">Đang thực hiện</option><option value="completed">Hoàn thành</option><option value="cancelled">Huỷ</option></select></div>
          </div>
          <div className="form-row">
            <div className="form-field"><label>Bắt đầu</label><input type="datetime-local" value={tripForm.timeStart} onChange={(event) => setTripForm((current) => ({ ...current, timeStart: event.target.value }))} /></div>
            <div className="form-field"><label>Kết thúc</label><input type="datetime-local" value={tripForm.timeEnd} onChange={(event) => setTripForm((current) => ({ ...current, timeEnd: event.target.value }))} /></div>
          </div>
          <div className="form-row">
            <div className="form-field"><label>Vĩ độ bắt đầu</label><input value={tripForm.latStart} onChange={(event) => setTripForm((current) => ({ ...current, latStart: event.target.value }))} placeholder="21.0285" /></div>
            <div className="form-field"><label>Kinh độ bắt đầu</label><input value={tripForm.lngStart} onChange={(event) => setTripForm((current) => ({ ...current, lngStart: event.target.value }))} placeholder="105.8542" /></div>
          </div>
          <div className="form-row">
            <div className="form-field"><label>Vĩ độ kết thúc</label><input value={tripForm.latEnd} onChange={(event) => setTripForm((current) => ({ ...current, latEnd: event.target.value }))} placeholder="21.0300" /></div>
            <div className="form-field"><label>Kinh độ kết thúc</label><input value={tripForm.lngEnd} onChange={(event) => setTripForm((current) => ({ ...current, lngEnd: event.target.value }))} placeholder="105.8600" /></div>
          </div>
          <div className="modal-actions"><button className="btn secondary" onClick={() => setTripModalOpen(false)}>Huỷ</button><button className="btn" onClick={() => void saveTrip()}>Lưu</button></div>
        </div>
      </div>

      <div className={`status-toast ${toast.visible ? 'show' : ''}`}>{toast.message}</div>
      <button className="mobile-toggle" onClick={() => setSidebarOpen((current) => !current)}>{sidebarOpen ? '✖' : '☰'}</button>
    </div>
  )
}

function MapClickHandler({ onClick }: { onClick: (lat: number, lng: number) => void }) {
  const map = useMap()
  useEffect(() => {
    map.on('click', (event: { latlng: { lat: number; lng: number } }) => {
      onClick(event.latlng.lat, event.latlng.lng)
    })
    return () => {
      map.off('click')
    }
  }, [map, onClick])
  return null
}

function FitBounds({ positions }: { positions: [number, number][] }) {
  const map = useMap()
  useEffect(() => {
    if (positions.length > 0) {
      map.fitBounds(positions)
    }
  }, [map, positions])
  return null
}

export default App
