import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type ComponentType,
  type ReactNode,
} from 'react';

import {
  User,
  Lock,
  ShieldCheck,
  Monitor,
  SlidersHorizontal,
  Link2,
  Globe,
  Code2,
  KeyRound,
  Bell,
  CreditCard,
  Check,
  Copy,
  Eye,
  EyeOff,
  Plus,
  ExternalLink,
  Trash2,
  AlertTriangle,
  Camera,
  X,
  ChevronRight,
  LogOut,
  Sparkles,
} from 'lucide-react';

import {
  setupTwoFactor,
  changePassword,
  disableTwoFactor,
  getTwoFactorStatus,
  verifyTwoFactorSetup,
} from '@/auth/authApi';

import { useAuth } from '@/auth/AuthContext';
import { QRCodeSVG } from 'qrcode.react';

type Section =
  | 'account'
  | 'link-defaults'
  | 'branded-links'
  | 'developer'
  | 'notifications'
  | 'billing';

type NavItem = {
  id: Section;
  label: string;
  description: string;
  icon: ComponentType<{
    size?: number;
    className?: string;
  }>;
};

const NAV_GROUPS: {
  label: string;
  items: NavItem[];
}[] = [
  {
    label: 'Account',
    items: [
      {
        id: 'account',
        label: 'Account',
        description: 'Profile, security and access',
        icon: User,
      },
    ],
  },
  {
    label: 'Links',
    items: [
      {
        id: 'link-defaults',
        label: 'Link defaults',
        description: 'Defaults for new links',
        icon: SlidersHorizontal,
      },
      {
        id: 'branded-links',
        label: 'Branded links',
        description: 'Custom domains',
        icon: Link2,
      },
    ],
  },
  {
    label: 'Developer',
    items: [
      {
        id: 'developer',
        label: 'Developer',
        description: 'API keys and integrations',
        icon: Code2,
      },
    ],
  },
  {
    label: 'Communication',
    items: [
      {
        id: 'notifications',
        label: 'Notifications',
        description: 'Email preferences',
        icon: Bell,
      },
    ],
  },
  {
    label: 'Billing',
    items: [
      {
        id: 'billing',
        label: 'Plan & billing',
        description: 'Plan, usage and billing',
        icon: CreditCard,
      },
    ],
  },
];

const ALL_NAV_ITEMS = NAV_GROUPS.flatMap(
  (group) => group.items,
);

/* ================================================================
   SHARED
================================================================ */

function Card({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={[
        'rounded-md border border-[#D2D2D7] bg-white',
        'shadow-[0_1px_2px_rgba(0,0,0,0.04)]',
        className,
      ].join(' ')}
    >
      {children}
    </div>
  );
}

function Button({
  children,
  onClick,
  variant = 'secondary',
  type = 'button',
  disabled = false,
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  type?: 'button' | 'submit';
  disabled?: boolean;
}) {
  const styles = {
    primary:
      'border-transparent bg-[#167A45] text-white hover:bg-[#12663A]',
    secondary:
      'border-[#D2D2D7] bg-white text-[#1D1D1F] hover:bg-[#F5F5F7]',
    danger:
      'border-[#E9C8C4] bg-white text-[#B42318] hover:bg-[#FFF7F6]',
    ghost:
      'border-transparent bg-transparent text-[#6E6E73] hover:bg-[#F5F5F7]',
  };

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={[
        'inline-flex h-9 items-center justify-center gap-2',
        'rounded-md border px-3.5',
        'text-[13px] font-medium transition-colors',
        'focus:outline-none focus:ring-2 focus:ring-[#167A45]/20',
        'disabled:cursor-not-allowed disabled:opacity-50',
        styles[variant],
      ].join(' ')}
    >
      {children}
    </button>
  );
}

function Toast({
  message,
}: {
  message: string;
}) {
  if (!message) return null;

  return (
    <div className="fixed bottom-6 left-1/2 z-100 flex -translate-x-1/2 items-center gap-2 rounded-lg bg-[#1D1D1F] px-4 py-3 text-sm font-medium text-white shadow-xl">
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[#167A45]">
        <Check size={12} />
      </span>

      {message}
    </div>
  );
}

function SectionHeader({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="mb-6">
      <h2 className="text-xl font-semibold tracking-tight text-[#1D1D1F]">
        {title}
      </h2>

      <p className="mt-1.5 text-sm leading-5 text-[#6E6E73]">
        {description}
      </p>
    </div>
  );
}

function SettingRow({
  icon,
  title,
  description,
  action,
  danger = false,
}: {
  icon?: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
  danger?: boolean;
}) {
  return (
    <div className="flex flex-col gap-4 border-b border-[#E8E8ED] px-6 py-5 last:border-b-0 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-start gap-3">
        {icon && (
          <div
            className={[
              'mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
              danger
                ? 'bg-[#FFF1F0] text-[#B42318]'
                : 'bg-[#F5F5F7] text-[#6E6E73]',
            ].join(' ')}
          >
            {icon}
          </div>
        )}

        <div>
          <p
            className={[
              'text-sm font-medium',
              danger ? 'text-[#8E2118]' : 'text-[#1D1D1F]',
            ].join(' ')}
          >
            {title}
          </p>

          <p className="mt-1 max-w-xl text-xs leading-5 text-[#86868B]">
            {description}
          </p>
        </div>
      </div>

      {action && (
        <div className="shrink-0 sm:ml-6">
          {action}
        </div>
      )}
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  disabled = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  disabled?: boolean;
}) {
  return (
    <div>
      <label className="mb-2 block text-xs font-semibold text-[#1D1D1F]">
        {label}
      </label>

      <input
        type={type}
        value={value}
        onChange={(event) =>
          onChange(event.target.value)
        }
        placeholder={placeholder}
        disabled={disabled}
        className="
          h-10 w-full rounded-md
          border border-[#D2D2D7]
          bg-white px-3
          text-sm text-[#1D1D1F]
          outline-none
          placeholder:text-[#86868B]
          focus:border-[#167A45]
          focus:ring-4 focus:ring-[#167A45]/8
          disabled:cursor-not-allowed disabled:bg-[#F5F5F7] disabled:text-[#86868B]
        "
      />
    </div>
  );
}

/* ================================================================
   CONFIRM DIALOG
================================================================ */

function ConfirmDialog({
  title,
  description,
  confirmLabel,
  onConfirm,
  onCancel,
  danger = false,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  danger?: boolean;
}) {
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancel();
      }
    };

    document.addEventListener('keydown', handler);

    return () =>
      document.removeEventListener(
        'keydown',
        handler,
      );
  }, [onCancel]);

  return (
    <div
      className="fixed inset-0 z-90 flex items-center justify-center bg-[#101828]/35 px-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
    >
      <div className="w-full max-w-md rounded-xl border border-[#E4E7EC] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div className="flex items-start justify-between">
          <div
            className={[
              'flex h-10 w-10 items-center justify-center rounded-md',
              danger
                ? 'bg-[#FFF1F0]'
                : 'bg-[#EDF7F0]',
            ].join(' ')}
          >
            {danger ? (
              <AlertTriangle
                size={19}
                className="text-[#B42318]"
              />
            ) : (
              <ShieldCheck
                size={19}
                className="text-[#167A45]"
              />
            )}
          </div>

          <button
            type="button"
            onClick={onCancel}
            className="rounded-md p-1.5 text-[#86868B] hover:bg-[#F5F5F7]"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <h3 className="mt-5 text-base font-semibold text-[#1D1D1F]">
          {title}
        </h3>

        <p className="mt-2 text-sm leading-6 text-[#6E6E73]">
          {description}
        </p>

        <div className="mt-6 flex justify-end gap-2">
          <Button onClick={onCancel}>
            Cancel
          </Button>

          <Button
            variant={danger ? 'danger' : 'primary'}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   ACCOUNT
================================================================ */

function AccountSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const {
    user,
    updateProfile,
    signOut,
  } = useAuth();

  const [name, setName] =
    useState('');

  const [email, setEmail] =
    useState('');

  const [photo, setPhoto] =
    useState<string | null>(null);

  const [twoFactor, setTwoFactor] =
    useState(false);

  const [
    isTwoFactorLoading,
    setIsTwoFactorLoading,
  ] = useState(true);

  const [
    twoFactorModal,
    setTwoFactorModal,
  ] = useState<
    'setup' | 'recovery' | 'disable' | null
  >(null);

  const [
    twoFactorSetup,
    setTwoFactorSetup,
  ] = useState<{
    secret: string;
    otpauthUri: string;
  } | null>(null);

  const [
    twoFactorCode,
    setTwoFactorCode,
  ] = useState('');

  const [recoveryCodes, setRecoveryCodes] =
    useState<string[]>([]);

  const [
    twoFactorError,
    setTwoFactorError,
  ] = useState('');

  const [
    isTwoFactorSubmitting,
    setIsTwoFactorSubmitting,
  ] = useState(false);

  const [passwordModal, setPasswordModal] =
    useState(false);

  const [deleteModal, setDeleteModal] =
    useState(false);

  const [saved, setSaved] =
    useState(false);

  const fileRef =
    useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!user) {
      return;
    }

    setName(user.fullName ?? '');
    setEmail(user.email);
  }, [user]);

  useEffect(() => {
    let cancelled = false;

    async function loadTwoFactorStatus() {
      setIsTwoFactorLoading(true);

      try {
        const status = await getTwoFactorStatus();

        if (!cancelled) {
          setTwoFactor(status.enabled);
        }
      } catch {
        if (!cancelled) {
          notify(
            'Unable to load two-factor authentication status.',
          );
        }
      } finally {
        if (!cancelled) {
          setIsTwoFactorLoading(false);
        }
      }
    }

    void loadTwoFactorStatus();

    return () => {
      cancelled = true;
    };
  }, [notify]);

  const startTwoFactorSetup = async () => {
    if (isTwoFactorSubmitting || isTwoFactorLoading) {
      return;
    }

    setTwoFactorError('');
    setTwoFactorCode('');
    setIsTwoFactorSubmitting(true);

    try {
      const setup = await setupTwoFactor();

      setTwoFactorSetup(setup);
      setTwoFactorModal('setup');
    } catch (error) {
      setTwoFactorError(
        error instanceof Error
          ? error.message
          : 'Unable to start two-factor authentication setup.',
      );
    } finally {
      setIsTwoFactorSubmitting(false);
    }
  };

  const verifySetup = async () => {
    const normalizedCode = twoFactorCode.trim();

    if (!/^\d{6}$/.test(normalizedCode)) {
      setTwoFactorError(
        'Enter the 6-digit authentication code.',
      );
      return;
    }

    setTwoFactorError('');
    setIsTwoFactorSubmitting(true);

    try {
      const response = await verifyTwoFactorSetup(normalizedCode);

      setTwoFactor(response.enabled);
      setTwoFactorSetup(null);
      setTwoFactorCode('');
      setRecoveryCodes(response.recoveryCodes);
      setTwoFactorModal('recovery');
    } catch (error) {
      setTwoFactorError(
        error instanceof Error
          ? error.message
          : 'The authentication code is incorrect.',
      );
      setTwoFactorCode('');
    } finally {
      setIsTwoFactorSubmitting(false);
    }
  };

  const disableTwoFactorAuth = async (password: string) => {
    const normalizedPassword = password;
    const normalizedCode = twoFactorCode.trim();

    if (!normalizedPassword) {
      setTwoFactorError('Enter your current password.');
      return;
    }

    if (!/^\d{6}$/.test(normalizedCode)) {
      setTwoFactorError(
        'Enter your current 6-digit authenticator code.',
      );
      return;
    }

    setTwoFactorError('');
    setIsTwoFactorSubmitting(true);

    try {
      await disableTwoFactor(normalizedPassword, normalizedCode);

      setTwoFactor(false);
      setTwoFactorModal(null);
      setTwoFactorCode('');

      notify('Two-factor authentication disabled. Please sign in again.');
      await signOut();
    } catch (error) {
      setTwoFactorError(
        error instanceof Error
          ? error.message
          : 'Unable to disable two-factor authentication.',
      );
      setTwoFactorCode('');
    } finally {
      setIsTwoFactorSubmitting(false);
    }
  };

  const handlePhoto = (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0];

    if (!file) return;

    const allowed = [
      'image/jpeg',
      'image/png',
      'image/gif',
    ];

    if (!allowed.includes(file.type)) {
      notify('Please choose a JPG, PNG, or GIF image.');
      event.target.value = '';
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      notify('Image must be smaller than 2MB.');
      event.target.value = '';
      return;
    }

    setPhoto(URL.createObjectURL(file));

    notify('Profile photo selected.');

    event.target.value = '';
  };

  const saveProfile = async () => {
    const trimmedName = name.trim();

    if (!trimmedName) {
      notify('Please enter your name.');
      return;
    }

    try {
      const updatedUser =
        await updateProfile(trimmedName);

      setName(updatedUser.fullName ?? '');
      setEmail(updatedUser.email);

      setSaved(true);
      notify('Profile updated successfully.');

      window.setTimeout(() => {
        setSaved(false);
      }, 1800);
    } catch {
      notify('Unable to update your profile.');
    }
  };

  return (
    <div className="space-y-6">
      <SectionHeader
        title="Account"
        description="Manage your personal information, security, and account access."
      />

      {/* Profile */}

      <Card>
        <div className="border-b border-[#E8E8ED] px-6 py-5">
          <p className="text-sm font-semibold text-[#1D1D1F]">
            Personal information
          </p>

          <p className="mt-1 text-xs text-[#86868B]">
            This information is associated with your Trimly account.
          </p>
        </div>

        <div className="p-6">
          <div className="mb-6 flex items-center gap-4">
            <div className="relative">
              <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-md border border-[#DCE5DF] bg-[#EDF7F0]">
                {photo ? (
                  <img
                    src={photo}
                    alt="Profile"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="text-xl font-bold text-[#167A45]">
                    I
                  </span>
                )}
              </div>

              <button
                type="button"
                onClick={() =>
                  fileRef.current?.click()
                }
                className="absolute -bottom-2 -right-2 flex h-7 w-7 items-center justify-center rounded-md border border-white bg-white text-[#6E6E73] shadow-md hover:text-[#167A45]"
                aria-label="Change profile photo"
              >
                <Camera size={13} />
              </button>

              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/gif"
                onChange={handlePhoto}
                className="hidden"
              />
            </div>

            <div>
              <p className="text-sm font-semibold text-[#1D1D1F]">
                {name}
              </p>

              <p className="mt-1 text-xs text-[#86868B]">
                {email}
              </p>

              <button
                type="button"
                onClick={() =>
                  fileRef.current?.click()
                }
                className="mt-2 text-xs font-medium text-[#167A45] hover:underline"
              >
                Change photo
              </button>
            </div>
          </div>

          <div className="grid gap-5 sm:grid-cols-2">
            <Field
              label="Full name"
              value={name}
              onChange={setName}
            />

            <Field
              label="Email address"
              type="email"
              value={email}
              onChange={setEmail}
              disabled
            />
          </div>

          <div className="mt-6 flex justify-end">
            <Button
              variant="primary"
              onClick={saveProfile}
            >
              {saved && <Check size={14} />}
              {saved ? 'Saved' : 'Save changes'}
            </Button>
          </div>
        </div>
      </Card>

      {/* Security */}

      <Card>
        <div className="border-b border-[#E8E8ED] px-6 py-5">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-md bg-[#EDF7F0]">
              <ShieldCheck
                size={17}
                className="text-[#167A45]"
              />
            </div>

            <div>
              <p className="text-sm font-semibold text-[#1D1D1F]">
                Security
              </p>

              <p className="mt-1 text-xs text-[#86868B]">
                Keep your account protected.
              </p>
            </div>
          </div>
        </div>

        <SettingRow
          icon={<KeyRound size={17} />}
          title="Password"
          description="Change your password if you think your account may be at risk."
          action={
            <Button
              onClick={() =>
                setPasswordModal(true)
              }
            >
              Change password
            </Button>
          }
        />

        <SettingRow
          icon={<ShieldCheck size={17} />}
          title="Two-factor authentication"
          description={
            twoFactor
              ? 'Your account requires an authenticator code when you sign in.'
              : 'Add an authenticator app for an additional layer of account security.'
          }
          action={
            isTwoFactorLoading ? (
              <span className="text-xs text-[#86868B]">
                Loading…
              </span>
            ) : (
              <Toggle
                checked={twoFactor}
                disabled={isTwoFactorSubmitting}
                onChange={(value) => {
                  if (value) {
                    void startTwoFactorSetup();
                  } else {
                    setTwoFactorError('');
                    setTwoFactorCode('');
                    setTwoFactorModal('disable');
                  }
                }}
                label="Two-factor authentication"
              />
            )
          }
        />

        <SettingRow
          icon={<Monitor size={17} />}
          title="Active sessions"
          description="Review devices currently signed in to your account."
          action={
            <Button
              onClick={() =>
                notify(
                  'Session management will be available soon.',
                )
              }
            >
              Manage
            </Button>
          }
        />
      </Card>

      {/* Danger zone */}

      <Card className="border-[#F0D0CC]">
        <div className="border-b border-[#F4DCD9] bg-[#FFF9F8] px-6 py-5">
          <p className="text-sm font-semibold text-[#8E2118]">
            Danger zone
          </p>

          <p className="mt-1 text-xs text-[#A35E58]">
            Permanent account actions.
          </p>
        </div>

        <SettingRow
          icon={<Trash2 size={17} />}
          title="Delete account"
          description="Permanently delete your account, short links, and associated data."
          danger
          action={
            <Button
              variant="danger"
              onClick={() =>
                setDeleteModal(true)
              }
            >
              Delete account
            </Button>
          }
        />
      </Card>

      {passwordModal && (
        <PasswordDialog
          onClose={() =>
            setPasswordModal(false)
          }
          onSuccess={() => {
            setPasswordModal(false);
            notify('Password updated successfully.');
          }}
        />
      )}

      {deleteModal && (
        <ConfirmDialog
          title="Delete your account?"
          description="This action is permanent. Your account, short links, and associated data will be removed."
          confirmLabel="Delete account"
          danger
          onCancel={() =>
            setDeleteModal(false)
          }
          onConfirm={() => {
            setDeleteModal(false);
            notify(
              'Account deletion requires server confirmation.',
            );
          }}
        />
      )}

      {twoFactorModal === 'setup' &&
        twoFactorSetup && (
          <TwoFactorSetupDialog
            setup={twoFactorSetup}
            code={twoFactorCode}
            error={twoFactorError}
            isSubmitting={isTwoFactorSubmitting}
            onCodeChange={(value) => {
              setTwoFactorCode(
                value.replace(/\D/g, '').slice(0, 6),
              );

              if (twoFactorError) {
                setTwoFactorError('');
              }
            }}
            onVerify={verifySetup}
            onClose={() => {
              if (isTwoFactorSubmitting) {
                return;
              }

              setTwoFactorModal(null);
              setTwoFactorSetup(null);
              setTwoFactorCode('');
              setTwoFactorError('');
            }}
          />
        )}

      {twoFactorModal === 'recovery' && (
        <TwoFactorRecoveryCodesDialog
          codes={recoveryCodes}
          onContinue={async () => {
            if (isTwoFactorSubmitting) {
              return;
            }

            setTwoFactorModal(null);
            setRecoveryCodes([]);
            notify('Two-factor authentication enabled. Please sign in again.');
            await signOut();
          }}
        />
      )}

      {twoFactorModal === 'disable' && (
        <TwoFactorDisableDialog
          code={twoFactorCode}
          error={twoFactorError}
          isSubmitting={isTwoFactorSubmitting}
          onCodeChange={(value) => {
            setTwoFactorCode(
              value.replace(/\D/g, '').slice(0, 6),
            );

            if (twoFactorError) {
              setTwoFactorError('');
            }
          }}
          onConfirm={disableTwoFactorAuth}
          onClose={() => {
            if (isTwoFactorSubmitting) {
              return;
            }

            setTwoFactorModal(null);
            setTwoFactorCode('');
            setTwoFactorError('');
          }}
        />
      )}
    </div>
  );
}

/* ================================================================
   TWO-FACTOR AUTHENTICATION
================================================================ */

function TwoFactorSetupDialog({
  setup,
  code,
  error,
  isSubmitting,
  onCodeChange,
  onVerify,
  onClose,
}: {
  setup: {
    secret: string;
    otpauthUri: string;
  };
  code: string;
  error: string;
  isSubmitting: boolean;
  onCodeChange: (value: string) => void;
  onVerify: () => void;
  onClose: () => void;
}) {
  const copySecret = async () => {
    try {
      await navigator.clipboard.writeText(setup.secret);
    } catch {
      // Clipboard may be unavailable.
    }
  };

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-[#101828]/35 px-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="two-factor-setup-title"
    >
      <div className="w-full max-w-md rounded-xl border border-[#E4E7EC] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-[#EDF7F0]">
              <ShieldCheck size={18} className="text-[#167A45]" />
            </div>

            <h3
              id="two-factor-setup-title"
              className="mt-4 text-base font-semibold text-[#1D1D1F]"
            >
              Set up two-factor authentication
            </h3>

            <p className="mt-1 text-xs leading-5 text-[#86868B]">
              Add Trimly to your authenticator app, then enter the generated
              6-digit code.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-md p-1.5 text-[#86868B] hover:bg-[#F5F5F7] disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <div className="mt-6 space-y-5">
          <div className="rounded-md border border-[#D2D2D7] bg-[#F5F5F7] p-4">
            <p className="text-xs font-semibold text-[#1D1D1F]">
              Scan the QR code
            </p>

            <p className="mt-1 text-xs leading-5 text-[#6E6E73]">
              Open your authenticator app and scan this code to add your Trimly account.
            </p>

            <div className="mt-4 flex justify-center rounded-md border border-[#D2D2D7] bg-white p-5">
              <QRCodeSVG
                value={setup.otpauthUri}
                size={190}
                level="M"
                includeMargin
                aria-label="Trimly two-factor authentication setup QR code"
              />
            </div>

            <div className="mt-4">
              <p className="text-[10px] font-semibold uppercase tracking-[0.08em] text-[#86868B]">
                Can't scan the QR code?
              </p>

              <div className="mt-1 flex items-center gap-2">
                <code className="min-w-0 flex-1 break-all rounded-md border border-[#D2D2D7] bg-white px-3 py-2 font-mono text-[11px] text-[#1D1D1F]">
                  {setup.secret}
                </code>

                <button
                  type="button"
                  onClick={copySecret}
                  className="shrink-0 rounded-md border border-[#D2D2D7] bg-white p-2 text-[#6E6E73] hover:bg-[#F5F5F7]"
                  aria-label="Copy setup key"
                >
                  <Copy size={15} />
                </button>
              </div>

              <p className="mt-2 text-[10px] leading-4 text-[#86868B]">
                You can enter this setup key manually in an authenticator app.
              </p>
            </div>
          </div>

          <div>
            <label
              htmlFor="two-factor-setup-code"
              className="mb-2 block text-xs font-semibold text-[#1D1D1F]"
            >
              Verification code
            </label>

            <input
              id="two-factor-setup-code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(event) => onCodeChange(event.target.value)}
              disabled={isSubmitting}
              placeholder="000000"
              className="h-11 w-full rounded-md border border-[#D2D2D7] bg-white px-3 text-center text-base font-medium tracking-[0.18em] text-[#1D1D1F] outline-none placeholder:text-[#A1A1A6] focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/8 disabled:cursor-not-allowed disabled:bg-[#F5F5F7]"
            />
          </div>

          {error && (
            <div
              role="alert"
              className="rounded-md bg-[#FFF6F5] px-3 py-2.5 text-xs font-medium text-[#B42318]"
            >
              {error}
            </div>
          )}
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>

          <Button
            variant="primary"
            onClick={onVerify}
            disabled={isSubmitting || code.length !== 6}
          >
            {isSubmitting ? 'Verifying…' : 'Enable 2FA'}
          </Button>
        </div>
      </div>
    </div>
  );
}

function TwoFactorDisableDialog({
  code,
  error,
  isSubmitting,
  onCodeChange,
  onConfirm,
  onClose,
}: {
  code: string;
  error: string;
  isSubmitting: boolean;
  onCodeChange: (value: string) => void;
  onConfirm: (password: string) => void;
  onClose: () => void;
}) {
  const [password, setPassword] = useState('');

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-[#101828]/35 px-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="two-factor-disable-title"
    >
      <div className="w-full max-w-md rounded-xl border border-[#E4E7EC] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-[#FFF1F0]">
              <AlertTriangle size={18} className="text-[#B42318]" />
            </div>

            <h3
              id="two-factor-disable-title"
              className="mt-4 text-base font-semibold text-[#1D1D1F]"
            >
              Disable two-factor authentication?
            </h3>

            <p className="mt-1 text-xs leading-5 text-[#86868B]">
              Confirm this security change with your current password and
              authenticator code.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-md p-1.5 text-[#86868B] hover:bg-[#F5F5F7] disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <div className="mt-6 space-y-4">
          <PasswordInput
            label="Current password"
            value={password}
            onChange={setPassword}
          />

          <div>
            <label
              htmlFor="two-factor-disable-code"
              className="mb-2 block text-xs font-semibold text-[#1D1D1F]"
            >
              Authentication code
            </label>

            <input
              id="two-factor-disable-code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(event) => onCodeChange(event.target.value)}
              disabled={isSubmitting}
              placeholder="000000"
              className="h-11 w-full rounded-md border border-[#D2D2D7] bg-white px-3 text-center text-base font-medium tracking-[0.18em] text-[#1D1D1F] outline-none placeholder:text-[#A1A1A6] focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/8 disabled:cursor-not-allowed disabled:bg-[#F5F5F7]"
            />
          </div>
        </div>

        {error && (
          <div
            role="alert"
            className="mt-4 rounded-md bg-[#FFF6F5] px-3 py-2.5 text-xs font-medium text-[#B42318]"
          >
            {error}
          </div>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>

          <Button
            variant="danger"
            onClick={() => onConfirm(password)}
            disabled={
              isSubmitting ||
              password.length === 0 ||
              code.length !== 6
            }
          >
            {isSubmitting ? 'Disabling…' : 'Disable 2FA'}
          </Button>
        </div>
      </div>
    </div>
  );
}

function TwoFactorRecoveryCodesDialog({
  codes,
  onContinue,
}: {
  codes: string[];
  onContinue: () => void | Promise<void>;
}) {
  const [copied, setCopied] = useState(false);

  const copyCodes = async () => {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-[#101828]/35 px-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="two-factor-recovery-title"
    >
      <div className="w-full max-w-md rounded-xl border border-[#E4E7EC] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-[#EDF7F0]">
            <ShieldCheck size={18} className="text-[#167A45]" />
          </div>

          <div>
            <h3
              id="two-factor-recovery-title"
              className="text-base font-semibold text-[#1D1D1F]"
            >
              Save your recovery codes
            </h3>

            <p className="mt-1 text-xs leading-5 text-[#86868B]">
              These codes can be used if you lose access to your authenticator.
              They will not be shown again after you leave this screen.
            </p>
          </div>
        </div>

        <div className="mt-6 rounded-md border border-[#D2D2D7] bg-[#F5F5F7] p-4">
          <div className="grid grid-cols-2 gap-2">
            {codes.map((code) => (
              <code
                key={code}
                className="rounded-md border border-[#D2D2D7] bg-white px-3 py-2 text-center font-mono text-xs font-medium text-[#1D1D1F]"
              >
                {code}
              </code>
            ))}
          </div>

          <button
            type="button"
            onClick={copyCodes}
            className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-md border border-[#D2D2D7] bg-white px-3 py-2 text-xs font-semibold text-[#1D1D1F] hover:bg-[#F5F5F7]"
          >
            {copied ? <Check size={14} /> : <Copy size={14} />}
            {copied ? 'Copied recovery codes' : 'Copy recovery codes'}
          </button>
        </div>

        <div className="mt-4 rounded-md border border-[#F0D0CC] bg-[#FFF9F8] p-3">
          <p className="text-xs leading-5 text-[#8E2118]">
            Store these codes somewhere safe. Each recovery code can be used
            only once.
          </p>
        </div>

        <div className="mt-6 flex justify-end">
          <Button
            variant="primary"
            onClick={onContinue}
            disabled={codes.length === 0}
          >
            I saved my recovery codes
          </Button>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   TOGGLE
================================================================ */

function Toggle({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={[
        'relative inline-flex h-6 w-10 items-center rounded-full transition-colors',
        'focus:outline-none focus:ring-2 focus:ring-[#167A45]/20',
        'disabled:cursor-not-allowed disabled:opacity-50',
        checked
          ? 'bg-[#167A45]'
          : 'bg-[#D4DAD6]',
      ].join(' ')}
    >
      <span
        className={[
          'h-4 w-4 rounded-full bg-white shadow-sm transition-transform',
          checked
            ? 'translate-x-5'
            : 'translate-x-1',
        ].join(' ')}
      />
    </button>
  );
}

/* ================================================================
   PASSWORD
================================================================ */

function PasswordDialog({
  onClose,
  onSuccess,
}: {
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submit = async () => {
    if (isSubmitting) {
      return;
    }

    setError('');

    if (!current) {
      setError('Enter your current password.');
      return;
    }

    if (next.length < 8) {
      setError(
        'New password must be at least 8 characters.',
      );
      return;
    }

    if (next.length > 72) {
      setError(
        'New password must be at most 72 characters.',
      );
      return;
    }

    if (next !== confirm) {
      setError('New passwords do not match.');
      return;
    }

    setIsSubmitting(true);

    try {
      await changePassword(current, next);

      onSuccess();
    } catch (error) {
      if (
        error instanceof Error &&
        'status' in error &&
        typeof (error as { status?: unknown }).status === 'number'
      ) {
        const status =
          (error as { status: number }).status;

        if (status === 401) {
          setError(
            'Your current password is incorrect.',
          );
        } else if (status === 400) {
          const message = error.message.trim();

          setError(
            message && message !== 'The request is invalid.'
              ? message
              : 'The password change request is invalid.',
          );
        } else if (status === 403) {
          setError(
            'You are not allowed to change the password.',
          );
        } else {
          setError(
            'Unable to change your password. Please try again.',
          );
        }
      } else if (error instanceof Error) {
        setError(
          error.message ||
            'Unable to change your password. Please try again.',
        );
      } else {
        setError(
          'Unable to change your password. Please try again.',
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-[#101828]/35 px-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="change-password-title"
    >
      <div className="w-full max-w-md rounded-xl border border-[#E4E7EC] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-[#EDF7F0]">
              <KeyRound
                size={18}
                className="text-[#167A45]"
              />
            </div>

            <h3
              id="change-password-title"
              className="mt-4 text-base font-semibold text-[#1D1D1F]"
            >
              Change password
            </h3>

            <p className="mt-1 text-xs leading-5 text-[#86868B]">
              Use a strong password you don't use elsewhere.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-md p-1.5 text-[#86868B] hover:bg-[#F5F5F7] disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <div className="mt-6 space-y-4">
          <PasswordInput
            label="Current password"
            value={current}
            onChange={setCurrent}
          />

          <PasswordInput
            label="New password"
            value={next}
            onChange={setNext}
          />

          <PasswordInput
            label="Confirm new password"
            value={confirm}
            onChange={setConfirm}
          />
        </div>

        {error && (
          <div
            className="mt-4 rounded-md bg-[#FFF6F5] px-3 py-2.5 text-xs font-medium text-[#B42318]"
            role="alert"
          >
            {error}
          </div>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            onClick={onClose}
            disabled={isSubmitting}
          >
            Cancel
          </Button>

          <Button
            variant="primary"
            onClick={submit}
            disabled={isSubmitting}
          >
            {isSubmitting
              ? 'Updating...'
              : 'Update password'}
          </Button>
        </div>
      </div>
    </div>
  );
}

function PasswordInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const [visible, setVisible] =
    useState(false);

  return (
    <div>
      <label className="mb-2 block text-xs font-semibold text-[#1D1D1F]">
        {label}
      </label>

      <div className="relative">
        <input
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(event) =>
            onChange(event.target.value)
          }
          className="
            h-10 w-full rounded-md
            border border-[#D2D2D7]
            px-3 pr-10 text-sm
            text-[#1D1D1F]
            outline-none
            focus:border-[#167A45]
            focus:ring-4 focus:ring-[#167A45]/8
          "
        />

        <button
          type="button"
          onClick={() =>
            setVisible(!visible)
          }
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-[#86868B] hover:bg-[#F5F5F7]"
          aria-label={
            visible
              ? 'Hide password'
              : 'Show password'
          }
        >
          {visible ? (
            <EyeOff size={16} />
          ) : (
            <Eye size={16} />
          )}
        </button>
      </div>
    </div>
  );
}

/* ================================================================
   LINK DEFAULTS
================================================================ */

function LinkDefaultsSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const [expiration, setExpiration] =
    useState('Never');

  const [aliasLength, setAliasLength] =
    useState('6');

  return (
    <div>
      <SectionHeader
        title="Link defaults"
        description="Set the defaults Trimly uses when you create a new short link."
      />

      <Card>
        <SettingRow
          icon={<Link2 size={17} />}
          title="Default link expiration"
          description="Choose when newly created links should expire."
          action={
            <select
              value={expiration}
              onChange={(event) => {
                setExpiration(event.target.value);
                notify('Expiration preference updated.');
              }}
              className="h-9 w-full rounded-md border border-[#D2D2D7] bg-white px-3 text-sm text-[#1D1D1F] outline-none focus:border-[#167A45] sm:w-40"
            >
              <option>Never</option>
              <option>7 days</option>
              <option>30 days</option>
              <option>90 days</option>
            </select>
          }
        />

        <SettingRow
          icon={<Code2 size={17} />}
          title="Default alias length"
          description="Control the length of automatically generated short-link aliases."
          action={
            <select
              value={aliasLength}
              onChange={(event) => {
                setAliasLength(event.target.value);
                notify('Alias length updated.');
              }}
              className="h-9 w-full rounded-md border border-[#D2D2D7] bg-white px-3 text-sm text-[#1D1D1F] outline-none focus:border-[#167A45] sm:w-40"
            >
              <option value="4">4 characters</option>
              <option value="6">6 characters</option>
              <option value="8">8 characters</option>
              <option value="10">10 characters</option>
            </select>
          }
        />
      </Card>

      <div className="mt-5 rounded-md border border-[#D2D2D7] bg-[#F5F5F7] p-4">
        <div className="flex items-start gap-3">
          <Sparkles
            size={17}
            className="mt-0.5 text-[#167A45]"
          />

          <div>
            <p className="text-xs font-semibold text-[#1D1D1F]">
              These are defaults, not restrictions
            </p>

            <p className="mt-1 text-xs leading-5 text-[#86868B]">
              You can override these values whenever you create an individual short link.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   BRANDED LINKS
================================================================ */

function BrandedLinksSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const [domain, setDomain] = useState('');
  const [connected, setConnected] = useState(false);

  const connect = () => {
    const value = domain.trim();

    if (!value) {
      notify('Enter your domain first.');
      return;
    }

    const pattern =
      /^(?!:\/\/)([a-zA-Z0-9-_]+\.)+[a-zA-Z]{2,}$/;

    if (!pattern.test(value)) {
      notify(
        'Enter a valid domain, such as links.example.com.',
      );
      return;
    }

    setConnected(true);
    notify('Domain saved. DNS verification is required.');
  };

  return (
    <div>
      <SectionHeader
        title="Branded links"
        description="Use your own domain to create short links that match your brand."
      />

      <Card className="overflow-hidden">
        <div className="bg-[#F5F5F7] px-6 py-6">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-white shadow-sm">
              <Globe
                size={18}
                className="text-[#167A45]"
              />
            </div>

            <div>
              <p className="text-sm font-semibold text-[#1D1D1F]">
                Custom domain
              </p>

              <p className="mt-1 text-xs leading-5 text-[#6E6E73]">
                Example: links.yourbrand.com/abc123
              </p>
            </div>
          </div>
        </div>

        <div className="p-6">
          <label className="mb-2 block text-xs font-semibold text-[#1D1D1F]">
            Domain
          </label>

          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              value={domain}
              onChange={(event) => {
                setDomain(event.target.value);
                setConnected(false);
              }}
              placeholder="links.yourdomain.com"
              className="h-10 flex-1 rounded-md border border-[#D2D2D7] px-3 text-sm outline-none placeholder:text-[#86868B] focus:border-[#167A45]"
            />

            <Button
              variant="primary"
              onClick={connect}
            >
              {connected && <Check size={14} />}
              {connected
                ? 'Saved'
                : 'Connect domain'}
            </Button>
          </div>

          {connected && (
            <div className="mt-5 rounded-md border border-[#D6E9DC] bg-[#F5FBF7] p-4">
              <div className="flex items-start gap-3">
                <Check
                  size={17}
                  className="mt-0.5 text-[#167A45]"
                />

                <div>
                  <p className="text-xs font-semibold text-[#167A45]">
                    DNS verification required
                  </p>

                  <p className="mt-1 text-xs leading-5 text-[#6E6E73]">
                    Add the DNS record provided by Trimly before this domain can be used.
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}

/* ================================================================
   DEVELOPER
================================================================ */

type ApiKey = {
  id: number;
  name: string;
  value: string;
};

function DeveloperSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [showCreate, setShowCreate] =
    useState(false);

  const [name, setName] = useState('');
  const [visible, setVisible] =
    useState<number | null>(null);

  const createKey = () => {
    const keyName =
      name.trim() ||
      `API key ${keys.length + 1}`;

    const randomPart = Math.random()
      .toString(36)
      .substring(2, 24);

    const key: ApiKey = {
      id: Date.now(),
      name: keyName,
      value: `trm_live_${randomPart}`,
    };

    setKeys((current) => [
      ...current,
      key,
    ]);

    setVisible(key.id);
    setName('');
    setShowCreate(false);

    notify('API key created. Copy it now.');
  };

  const copy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      notify('API key copied.');
    } catch {
      notify('Clipboard access is unavailable.');
    }
  };

  return (
    <div>
      <SectionHeader
        title="Developer"
        description="Manage programmatic access and connect Trimly with your applications."
      />

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-4 border-b border-[#E8E8ED] px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-semibold text-[#1D1D1F]">
              API keys
            </p>

            <p className="mt-1 text-xs text-[#86868B]">
              Authenticate requests made to the Trimly API.
            </p>
          </div>

          <Button
            variant="primary"
            onClick={() =>
              setShowCreate(true)
            }
          >
            <Plus size={15} />
            Create key
          </Button>
        </div>

        {showCreate && (
          <div className="border-b border-[#E8E8ED] bg-[#F5F5F7] px-6 py-5">
            <label className="mb-2 block text-xs font-semibold text-[#1D1D1F]">
              Key name
            </label>

            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                value={name}
                onChange={(event) =>
                  setName(event.target.value)
                }
                placeholder="My application"
                autoFocus
                className="h-10 flex-1 rounded-md border border-[#D2D2D7] bg-white px-3 text-sm outline-none focus:border-[#167A45]"
              />

              <Button
                variant="primary"
                onClick={createKey}
              >
                Generate
              </Button>

              <Button
                onClick={() => {
                  setShowCreate(false);
                  setName('');
                }}
              >
                Cancel
              </Button>
            </div>
          </div>
        )}

        {keys.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-lg bg-[#F5F5F7]">
              <KeyRound
                size={20}
                className="text-[#667C70]"
              />
            </div>

            <p className="mt-4 text-sm font-semibold text-[#1D1D1F]">
              No API keys
            </p>

            <p className="mx-auto mt-1 max-w-sm text-xs leading-5 text-[#86868B]">
              Create a key when you need programmatic access to Trimly.
            </p>
          </div>
        ) : (
          <div>
            {keys.map((key) => {
              const isVisible =
                visible === key.id;

              return (
                <div
                  key={key.id}
                  className="flex items-center gap-3 border-b border-[#E8E8ED] px-6 py-4 last:border-b-0"
                >
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[#F5F5F7]">
                    <KeyRound
                      size={15}
                      className="text-[#667C70]"
                    />
                  </div>

                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-[#1D1D1F]">
                      {key.name}
                    </p>

                    <p className="mt-1 truncate font-mono text-[11px] text-[#86868B]">
                      {isVisible
                        ? key.value
                        : '••••••••••••••••••'}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      setVisible(
                        isVisible
                          ? null
                          : key.id,
                      )
                    }
                    className="rounded-md p-2 text-[#86868B] hover:bg-[#F5F5F7]"
                    aria-label={
                      isVisible
                        ? 'Hide API key'
                        : 'Show API key'
                    }
                  >
                    {isVisible ? (
                      <EyeOff size={16} />
                    ) : (
                      <Eye size={16} />
                    )}
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      copy(key.value)
                    }
                    className="rounded-md p-2 text-[#86868B] hover:bg-[#F5F5F7]"
                    aria-label="Copy API key"
                  >
                    <Copy size={16} />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      <Card className="mt-5 p-5">
        <div className="flex items-start gap-3">
          <Code2
            size={18}
            className="mt-0.5 text-[#6E6E73]"
          />

          <div>
            <p className="text-sm font-semibold text-[#1D1D1F]">
              API documentation
            </p>

            <p className="mt-1 text-xs leading-5 text-[#86868B]">
              Learn how to create links, retrieve analytics, and integrate Trimly into your application.
            </p>

            <button
              type="button"
              onClick={() =>
                notify(
                  'API documentation will open here.',
                )
              }
              className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-[#167A45] hover:underline"
            >
              View documentation
              <ExternalLink size={13} />
            </button>
          </div>
        </div>
      </Card>
    </div>
  );
}

/* ================================================================
   NOTIFICATIONS
================================================================ */

function NotificationsSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const [linkActivity, setLinkActivity] =
    useState(true);

  const [security, setSecurity] =
    useState(true);

  const [product, setProduct] =
    useState(false);

  return (
    <div>
      <SectionHeader
        title="Notifications"
        description="Choose which emails Trimly should send you."
      />

      <Card>
        <SettingRow
          icon={<Link2 size={17} />}
          title="Link activity"
          description="Receive important updates about your links and traffic."
          action={
            <Toggle
              checked={linkActivity}
              onChange={(value) => {
                setLinkActivity(value);
                notify(
                  value
                    ? 'Link activity enabled.'
                    : 'Link activity disabled.',
                );
              }}
              label="Link activity notifications"
            />
          }
        />

        <SettingRow
          icon={<ShieldCheck size={17} />}
          title="Security alerts"
          description="Important notifications about password changes and account security."
          action={
            <Toggle
              checked={security}
              onChange={(value) => {
                setSecurity(value);
                notify(
                  value
                    ? 'Security alerts enabled.'
                    : 'Security alerts disabled.',
                );
              }}
              label="Security alerts"
            />
          }
        />

        <SettingRow
          icon={<Sparkles size={17} />}
          title="Product updates"
          description="Occasional updates about new Trimly features and improvements."
          action={
            <Toggle
              checked={product}
              onChange={(value) => {
                setProduct(value);
                notify(
                  value
                    ? 'Product updates enabled.'
                    : 'Product updates disabled.',
                );
              }}
              label="Product updates"
            />
          }
        />
      </Card>

      <div className="mt-5 rounded-md border border-[#D2D2D7] bg-[#F5F5F7] p-4">
        <div className="flex items-start gap-3">
          <Bell
            size={17}
            className="mt-0.5 text-[#6E6E73]"
          />

          <p className="text-xs leading-5 text-[#6E6E73]">
            Some security-related emails may still be sent when they're necessary to protect your account.
          </p>
        </div>
      </div>
    </div>
  );
}

/* ================================================================
   BILLING
================================================================ */

function BillingSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  return (
    <div>
      <SectionHeader
        title="Plan & billing"
        description="Manage your Trimly plan, usage, and billing information."
      />

      <div className="space-y-5">
        <Card className="overflow-hidden">
          <div className="bg-[#F5F5F7] p-6">
            <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-semibold text-[#1D1D1F]">
                    Free plan
                  </p>

                  <span className="rounded-full bg-[#E8F5EC] px-2 py-0.5 text-[10px] font-semibold text-[#167A45]">
                    Current
                  </span>
                </div>

                <p className="mt-1.5 text-xs text-[#6E6E73]">
                  Everything you need to get started.
                </p>
              </div>

              <Button
                variant="primary"
                onClick={() =>
                  notify(
                    'Upgrade checkout would open here.',
                  )
                }
              >
                Upgrade plan
                <ChevronRight size={14} />
              </Button>
            </div>
          </div>
        </Card>

        <Card>
          <div className="border-b border-[#E8E8ED] px-6 py-5">
            <p className="text-sm font-semibold text-[#1D1D1F]">
              Usage
            </p>

            <p className="mt-1 text-xs text-[#86868B]">
              Your current usage on the Free plan.
            </p>
          </div>

          <div className="grid divide-y divide-[#EEF0EF] sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <Usage
              label="Short links"
              value="248"
              limit="1,000"
              percentage={25}
            />

            <Usage
              label="Clicks"
              value="12,842"
              limit="50,000"
              percentage={26}
            />

            <Usage
              label="Custom domains"
              value="0"
              limit="1"
              percentage={0}
            />
          </div>
        </Card>

        <Card>
          <SettingRow
            icon={<CreditCard size={17} />}
            title="Billing information"
            description="Manage payment methods and billing details."
            action={
              <Button
                onClick={() =>
                  notify(
                    'Billing management will be available here.',
                  )
                }
              >
                Manage billing
              </Button>
            }
          />

          <SettingRow
            icon={<LogOut size={17} />}
            title="Billing history"
            description="View previous invoices and payments."
            action={
              <Button
                onClick={() =>
                  notify(
                    'Billing history will be available here.',
                  )
                }
              >
                View history
              </Button>
            }
          />
        </Card>
      </div>
    </div>
  );
}

function Usage({
  label,
  value,
  limit,
  percentage,
}: {
  label: string;
  value: string;
  limit: string;
  percentage: number;
}) {
  return (
    <div className="p-6">
      <p className="text-xs font-medium text-[#86868B]">
        {label}
      </p>

      <p className="mt-2 text-2xl font-semibold tracking-[-0.03em] text-[#1D1D1F]">
        {value}
        <span className="text-xs font-normal tracking-normal text-[#86868B]">
          {' '}
          / {limit}
        </span>
      </p>

      <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-[#EEF1EF]">
        <div
          className="h-full rounded-full bg-[#167A45]"
          style={{
            width: `${percentage}%`,
          }}
        />
      </div>
    </div>
  );
}

/* ================================================================
   SETTINGS PAGE
================================================================ */

export function SettingsPage() {
  const [activeSection, setActiveSection] =
    useState<Section>('account');

  const [toast, setToast] = useState('');

  const notify = useCallback((message: string) => {
    setToast(message);

    window.setTimeout(() => {
      setToast('');
    }, 2200);
  }, []);

  const activeItem =
    ALL_NAV_ITEMS.find(
      (item) => item.id === activeSection,
    );

  const renderSection = () => {
    switch (activeSection) {
      case 'account':
        return (
          <AccountSection notify={notify} />
        );

      case 'link-defaults':
        return (
          <LinkDefaultsSection
            notify={notify}
          />
        );

      case 'branded-links':
        return (
          <BrandedLinksSection
            notify={notify}
          />
        );

      case 'developer':
        return (
          <DeveloperSection
            notify={notify}
          />
        );

      case 'notifications':
        return (
          <NotificationsSection
            notify={notify}
          />
        );

      case 'billing':
        return (
          <BillingSection
            notify={notify}
          />
        );

      default:
        return null;
    }
  };

  return (
    <main className="min-h-[calc(100vh-64px)] bg-[#F5F5F7]">
      <div className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">

        {/* Header */}

        <div className="mb-8">
          <div className="flex items-center gap-2 text-xs text-[#86868B]">
            <span>Workspace</span>

            <ChevronRight size={13} />

            <span className="font-medium text-[#424245]">
              Settings
            </span>
          </div>

          <h1 className="mt-3 text-3xl font-semibold tracking-[-0.035em] text-[#1D1D1F]">
            Settings
          </h1>

          <p className="mt-1.5 text-sm text-[#6E6E73]">
            Manage your Trimly account and workspace.
          </p>
        </div>

        {/* Mobile navigation */}

        <div className="mb-6 md:hidden">
          <select
            value={activeSection}
            onChange={(event) =>
              setActiveSection(
                event.target.value as Section,
              )
            }
            className="
              h-11 w-full rounded-md
              border border-[#D2D2D7]
              bg-white px-3
              text-sm font-medium text-[#1D1D1F]
              outline-none
              focus:border-[#167A45]
            "
          >
            {ALL_NAV_ITEMS.map((item) => (
              <option
                key={item.id}
                value={item.id}
              >
                {item.label}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-[210px_minmax(0,1fr)] lg:gap-8">

          {/* Navigation */}

          <aside className="hidden md:block">
            <nav
              aria-label="Settings navigation"
              className="sticky top-6 space-y-5"
            >
              {NAV_GROUPS.map((group) => (
                <div key={group.label}>
                  <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[0.14em] text-[#86868B]">
                    {group.label}
                  </p>

                  <div className="space-y-1">
                    {group.items.map((item) => {
                      const Icon = item.icon;
                      const active =
                        activeSection ===
                        item.id;

                      return (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() =>
                            setActiveSection(
                              item.id,
                            )
                          }
                          aria-current={
                            active
                              ? 'page'
                              : undefined
                          }
                          className={[
                            'group flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left transition-all',
                            active
                              ? 'bg-white text-[#1D1D1F] shadow-[0_1px_3px_rgba(0,0,0,0.06)]'
                              : 'text-[#6E6E73] hover:bg-white hover:text-[#1D1D1F]',
                          ].join(' ')}
                        >
                          <span
                            className={[
                              'flex h-8 w-8 shrink-0 items-center justify-center rounded-md',
                              active
                                ? 'bg-[#E8F5EC] text-[#167A45]'
                                : 'text-[#86868B] group-hover:bg-[#F5F5F7]',
                            ].join(' ')}
                          >
                            <Icon size={16} />
                          </span>

                          <span className="min-w-0">
                            <span
                              className={[
                                'block text-sm font-medium',
                                active
                                  ? 'text-[#167A45]'
                                  : 'text-[#424245]',
                              ].join(' ')}
                            >
                              {item.label}
                            </span>

                            <span className="mt-0.5 block truncate text-[10px] text-[#86868B]">
                              {item.description}
                            </span>
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}

              <div className="rounded-md border border-[#D2D2D7] bg-white p-4">
                <div className="flex h-8 w-8 items-center justify-center rounded-md bg-[#EDF7F0]">
                  <Sparkles
                    size={15}
                    className="text-[#167A45]"
                  />
                </div>

                <p className="mt-3 text-xs font-semibold text-[#1D1D1F]">
                  Need help?
                </p>

                <p className="mt-1 text-[11px] leading-5 text-[#86868B]">
                  Find setup and API guidance in the Trimly documentation.
                </p>
              </div>
            </nav>
          </aside>

          {/* Main content */}

          <section className="min-w-0">
            {renderSection()}
          </section>
        </div>
      </div> 
      <Toast message={toast} />
    </main>
  );
}  