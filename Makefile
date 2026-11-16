# Ingang voor de dagelijkse lus. Lokaal Node 24 uit Homebrew (keg-only), als die er is; in CI zet setup-node hem.
NODE24 := /opt/homebrew/opt/node@24/bin
ifneq ($(wildcard $(NODE24)/node),)
export PATH := $(NODE24):$(PATH)
endif

BACKEND  := backend
FRONTEND := frontend
E2E      := e2e

.PHONY: help test backend-test mutatie frontend-test iac commit-stage install uninstall e2e-install e2e e2e-infra acceptance handleiding allure grafana verkeer

help: ## Overzicht van de targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

test: backend-test frontend-test ## Backend- en frontendtests

backend-test: ## mvnw verify
	$(BACKEND)/mvnw -q -B -ntp -f $(BACKEND)/pom.xml verify

mutatie: ## Mutation testing met PIT, faalt onder 95% per module (ADR 0018)
	$(BACKEND)/mvnw -B -ntp -f $(BACKEND)/pom.xml test-compile org.pitest:pitest-maven:mutationCoverage

frontend-test: ## De types uit het API-contract (ADR 0034), Angular lint, Vitest en build
	npm --prefix api ci
	npm --prefix api run types
	npm --prefix $(FRONTEND) run lint
	npm --prefix $(FRONTEND) test
	npm --prefix $(FRONTEND) run build

iac: ## Chart-, script- en workflowchecks in containers (ADR 0006)
	scripts/iac-check.sh

commit-stage: test mutatie iac ## Alles wat de commit stage in CI draait
	npm --prefix $(E2E) run typecheck
	scripts/lagen-check.sh

install: ## BankSim-MSE installeren in namespace banksim-mse van kind-single-node
	deploy/scripts/install.sh

uninstall: ## BankSim-MSE verwijderen (alleen namespace banksim-mse)
	deploy/scripts/uninstall.sh

e2e-install: ## Playwright en browsers installeren
	npm --prefix $(E2E) ci
	npm --prefix $(E2E) exec -- playwright install chromium firefox webkit

e2e: ## Acceptatietests tegen https://bank-mse.localtest.me (wist eerst de vorige Allure-resultaten)
	rm -rf $(E2E)/allure-results
	npm --prefix $(E2E) test

e2e-infra: ## Installatiescenario's tegen kind-single-node (zonder @destructief)
	npm --prefix $(E2E) run test:infra

handleiding: ## De schermafdrukken van doc/banksim-mse-handleiding.md opnieuw maken, tegen de installatie (alleen kijken, wijzigt niets)
	npm --prefix $(E2E) run handleiding

allure: ## Allure-rapport van de laatste lokale run(s) in e2e/allure-results maken en openen (ADR 0053)
	npm --prefix $(E2E) exec -- allure generate --clean $(E2E)/allure-results -o $(E2E)/allure-report
	npm --prefix $(E2E) exec -- allure open $(E2E)/allure-report

grafana: ## Grafana van otel-lgtm openen op http://localhost:3000 via een port-forward; Ctrl-C stopt hem (ADR 0054)
	@echo "Grafana: http://localhost:3000"
	kubectl --context kind-single-node -n banksim-mse port-forward svc/otel-lgtm 3000:3000

# Niet tegelijk met make e2e: de simulatiedatum geldt voor de hele bank (ADR 0014).
verkeer: ## Doorlopend verkeer met k6 (ADR 0055); SNELHEID=bezoeken per seconde (2), DUUR (10m); Ctrl-C stopt het
	BANKSIM_VERKEER=true deploy/scripts/install.sh --skip-build --skip-smoke-tests
	deploy/scripts/verkeer.sh --snelheid $(or $(SNELHEID),2) --duur $(or $(DUUR),10m)

acceptance: install e2e e2e-infra ## Installeren en daarna de acceptatie- en installatiescenario's, zoals de acceptance stage in CI
