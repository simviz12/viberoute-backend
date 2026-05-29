# Guía de Despliegue Rápido - VibeRoute EC2

Sigue estos pasos exactos una vez que hayas clonado este repositorio en tu instancia de Ubuntu.

## 1. Instalación de Requisitos
```bash
sudo apt update
sudo apt install docker.io docker-compose -y
sudo usermod -aG docker $USER
# REINICIA TU SESIÓN (Sal y vuelve a entrar)
```

## 2. Configuración de Secretos
Crea el archivo de variables:
```bash
cp .env.example .env
nano .env
# Pega tus llaves reales y guarda
```

Sube manualmente el archivo `serviceAccountKey.json` de Firebase a esta carpeta (usa SCP o arrastra el archivo si usas una terminal con soporte).

## 3. Despliegue
```bash
docker-compose up --build -d
```

## 4. Verificación
* Revisa que el backend responda en: `http://<TU_IP_EC2>:8080/api/v1/ping`
* Revisa los logs en caso de error: `docker logs -f viberoute-backend`

## IMPORTANTE: AWS Security Group
Asegúrate de abrir el puerto **8080** en la consola de AWS (Inbound Rules) para que el tráfico pueda entrar.
