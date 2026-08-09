.PHONY: up down reset logs backend frontend

up:
	docker compose up --build

down:
	docker compose down

reset:
	docker compose down -v

logs:
	docker compose logs -f backend-api

backend:
	cd backend && mvn spring-boot:run

frontend:
	cd frontend && npm install && npm run dev
