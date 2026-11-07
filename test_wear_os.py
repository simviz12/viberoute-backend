import urllib.request
import urllib.error
import json
import base64

def req(url, data=None, token=None, method="GET"):
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    bdata = json.dumps(data).encode("utf-8") if data else None
    r = urllib.request.Request(url, data=bdata, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, body

def main():
    p_b64 = base64.b64encode("VibeRoute2026!".encode()).decode()

    # 1. Login superadmin
    st, admin_res = req("http://localhost:8080/api/v1/auth/login", {"email": "superadmin@viberoute.com", "password": p_b64}, method="POST")
    assert st == 200, f"Login superadmin failed: {st}"
    admin_token = admin_res["token"]
    print(f"[OK] Admin login exitoso (Token obtenido)")

    # 2. Manual batch with orders [1, 2]
    st, batch = req("http://localhost:8080/api/v1/batches/manual", {"city": "Bogotá", "orderIds": [1, 2]}, token=admin_token, method="POST")
    assert st in [200, 201], f"Batch creation failed: {st} - {batch}"
    batch_id = batch["id"]
    print(f"[OK] Lote manual creado con ID: {batch_id}")

    # 3. Assign driver 3 (driver@viberoute.com)
    st, assign = req(f"http://localhost:8080/api/v1/batches/{batch_id}/assign-driver/3", token=admin_token, method="POST")
    assert st in [200, 201], f"Assign driver failed: {st}"
    print(f"[OK] Conductor 3 asignado al lote #{batch_id}")

    # 4. Optimize batch route
    st, opt = req(f"http://localhost:8080/api/v1/batches/{batch_id}/optimize", token=admin_token, method="POST")
    assert st in [200, 201], f"Optimize batch failed: {st}"
    print(f"[OK] Ruta del lote #{batch_id} optimizada exitosamente")

    # 5. Login Driver 3
    st, drv_res = req("http://localhost:8080/api/v1/auth/login", {"email": "driver@viberoute.com", "password": p_b64}, method="POST")
    assert st == 200, f"Driver login failed: {st}"
    drv_token = drv_res["token"]
    print(f"[OK] Login conductor exitoso")

    # 6. GET /api/wearable/routes/active
    print("\n--- ⌚ PROBANDO GET /api/wearable/routes/active ---")
    st, active_route = req("http://localhost:8080/api/wearable/routes/active", token=drv_token)
    assert st == 200, f"GET active route failed: {st} - {active_route}"
    print(f"[OK] 200 OK - Ruta activa sincronizada")
    print(f"     > Route ID: {active_route.get('routeId')}")
    print(f"     > Batch ID: {active_route.get('batchId')}")
    print(f"     > Conductor: {active_route.get('driverName')} (ID: {active_route.get('driverId')})")
    print(f"     > Total Paradas: {active_route.get('totalStops')}")
    print(f"     > Paradas Pendientes: {active_route.get('pendingStops')}")
    print(f"     > Paradas Completadas: {active_route.get('completedStops')}")
    print(f"     > Distancia Total: {active_route.get('totalDistanceMeters')} m")
    print("     > Secuencia de paradas:")
    for s in active_route.get("stops", []):
        print(f"       * Secuencia #{s.get('deliveryOrder')}: Entrega #{s.get('deliveryId')} ({s.get('address')}) - Estado: {s.get('status')}")

    # 7. POST /api/wearable/deliveries/1/status (DELIVERED)
    print("\n--- ⌚ PROBANDO POST /api/wearable/deliveries/1/status (DELIVERED) ---")
    delivery_payload_1 = {
        "status": "DELIVERED",
        "timestamp": "2026-11-07T18:30:00",
        "notes": "Entregado a recepcionista Maria"
    }
    st, d1 = req("http://localhost:8080/api/wearable/deliveries/1/status", delivery_payload_1, token=drv_token, method="POST")
    assert st == 200, f"Delivery 1 failed: {st} - {d1}"
    print(f"[OK] 200 OK - Entrega 1 confirmada como DELIVERED")
    print(f"     > Entrega ID: {d1.get('deliveryId')}")
    print(f"     > Estado: {d1.get('status')}")
    print(f"     > Dirección: {d1.get('address')}")

    # 8. POST /api/wearable/deliveries/2/status (ABSENT)
    print("\n--- ⌚ PROBANDO POST /api/wearable/deliveries/2/status (ABSENT) ---")
    delivery_payload_2 = {
        "status": "ABSENT",
        "timestamp": "2026-11-07T18:35:00",
        "notes": "Destinatario no se encuentra en apto 204"
    }
    st, d2 = req("http://localhost:8080/api/wearable/deliveries/2/status", delivery_payload_2, token=drv_token, method="POST")
    assert st == 200, f"Delivery 2 failed: {st} - {d2}"
    print(f"[OK] 200 OK - Entrega 2 confirmada como ABSENT (mapeado a RETURNED con motivo)")
    print(f"     > Entrega ID: {d2.get('deliveryId')}")
    print(f"     > Estado: {d2.get('status')}")
    print(f"     > Motivo: {d2.get('notes')}")

    print("\n=== TODAS LAS PRUEBAS WEAR OS PASARON SATISFACTORIAMENTE ===")

if __name__ == "__main__":
    main()

