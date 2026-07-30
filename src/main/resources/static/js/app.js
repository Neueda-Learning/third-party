import { initNavigation, showModule } from './modules/navigation.js';
import { initCreatePaymentModule } from './modules/create-payment.js';
import { initPaymentListModule } from './modules/payment-list.js';
import { initPaymentDetailModule } from './modules/payment-detail.js';

function bootstrap() {
    initNavigation();
    initCreatePaymentModule();
    initPaymentListModule();
    initPaymentDetailModule();
    showModule('create');
}

document.addEventListener('DOMContentLoaded', bootstrap);

