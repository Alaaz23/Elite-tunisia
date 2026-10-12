export class ApiHttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = 'ApiHttpError';
  }
}