# Ingang voor de dagelijkse lus. Lokaal Node 24 uit Homebrew (keg-only), als die er is; in CI zet setup-node hem.
NODE24 := /opt/homebrew/opt/node@24/bin
ifneq ($(wildcard $(NODE24)/node),)
export PATH := $(NODE24):$(PATH)
endif

BACKEND  := backend
FRONTEND := frontend
E2E      := e2e

.PHONY: help test backend-test mutatie frontend-test iac commit-stage install uninstall e2e-install e2e e2e-infra acceptance

help: ## Overzicht van de targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

test: backend-test frontend-test ## Backend- en frontendtests

backend-test: ## mvnw verify
	$(BACKEND)/mvnw -q -B -ntp -f $(BACKEND)/pom.xml verify

mutatie: ## Mutation testing met PIT, faalt onder 95% per module (ADR 0018)
	$(BACKEND)/mvnw -B -ntp -f $(BACKEND)/pom.xml test-compile org.pitest:pitest-maven:mutationCoverage

frontend-test: ## Angular lint, Vitest en build
	npm --prefix $(FRONTEND) run lint
	npm --prefix $(FRONTEND) test
	npm --prefix $(FRONTEND) run build

iac: ## Chart-, script- en workflowchecks in containers (ADR 0006)
	scripts/iac-check.sh

commit-stage: test mutatie iac ## Alles wat de commit stage in CI draait
	npm --prefix $(E2E) run typecheck

install: ## BankSim-MSE installeren in namespace banksim-mse van kind-single-node
	deploy/scripts/install.sh

uninstall: ## BankSim-MSE verwijderen (alleen namespace banksim-mse)
	deploy/scripts/uninstall.sh

e2e-install: ## Playwright en browsers installeren
	npm --prefix $(E2E) ci
	npm --prefix $(E2E) exec -- playwright install chromium firefox webkit

e2e: ## Acceptatietests tegen https://bank-mse.localtest.me
	npm --prefix $(E2E) test

e2e-infra: ## Installatiescenario's tegen kind-single-node (zonder @destructief)
	npm --prefix $(E2E) run test:infra

acceptance: install e2e e2e-infra ## Installeren en daarna de acceptatie- en installatiescenario's, zoals de acceptance stage in CI
