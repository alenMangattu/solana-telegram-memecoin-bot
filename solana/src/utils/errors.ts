export class InsufficientFundsError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'InsufficientFundsError';
    }
}

export class InvalidAddressError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'InvalidAddressError';
    }
} 